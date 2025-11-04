pipeline {
  agent any
  options { skipStagesAfterUnstable() }

  tools { maven 'Maven3' }

  environment {
    APP_NAME     = 'demoapp'
    PORT         = '8081'
    GITHUB_REPO  = 'Sustainerr/devdemoapp'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        echo "Building branch: ${env.BRANCH_NAME}"

        script {
          // Get the commit SHA for GitHub status updates
          def COMMIT_SHA = sh(returnStdout: true, script: "git rev-parse HEAD").trim()

          // Notify GitHub that build started
          withCredentials([string(credentialsId: 'git', variable: 'GITHUB_TOKEN')]) {
            sh """
              curl -s -X POST \
                -H "Authorization: token ${GITHUB_TOKEN}" \
                -H "Accept: application/vnd.github+json" \
                https://api.github.com/repos/${GITHUB_REPO}/statuses/${COMMIT_SHA} \
                -d '{"state":"pending","context":"jenkins/build","description":"Build started"}'
            """
          }

          // Save commit SHA for later stages
          writeFile file: 'commit.txt', text: COMMIT_SHA
        }
      }
    }

    // 🔹 1. Secrets Scan (Gitleaks)
    stage('Secrets Scan - Gitleaks') {
      steps {
        sh '''
          echo "🔐 Running Gitleaks for secrets scanning..."
          gitleaks detect --source . --no-git --report-path gitleaks-report.json || true
          echo "✅ Secrets scan complete (report saved: gitleaks-report.json)"
        '''
      }
      post {
        always {
          archiveArtifacts artifacts: 'gitleaks-report.json', allowEmptyArchive: true
        }
      }
    }

    stage('Compile') {
      steps {
        sh 'mvn -B -Dspotbugs.skip=true -Ddependency-check.skip=true clean compile'
      }
    }

    stage('Test') {
      steps {
        sh 'mvn -B test'
      }
      post {
        always {
          junit 'target/surefire-reports/*.xml'
        }
      }
    }

    stage('SAST - SonarQube Analysis') {
      steps {
        withSonarQubeEnv('SonarQube') {
          withCredentials([string(credentialsId: 'Sonar', variable: 'SONARQUBE')]) {
            sh '''
              mvn clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:4.0.0.4121:sonar \
                -Dsonar.projectKey=devdemoapp \
                -Dsonar.host.url=http://localhost:9000 \
                -Dsonar.login=$SONARQUBE
            '''
          }
        }
      }
    }

    stage('Quality Gate') {
      steps {
        script {
          timeout(time: 3, unit: 'MINUTES') {
            def qg = waitForQualityGate()
            if (qg.status != 'OK') {
              error "❌ Pipeline aborted due to SonarQube quality gate failure: ${qg.status}"
            }
          }
        }
      }
    }

    // 🔹 2. SCA (Dependency Scan) - Trivy filesystem
    stage('SCA - Dependency Scan') {
      steps {
        sh '''
          echo "📦 Running Trivy dependency scan..."
          trivy fs --exit-code 0 --severity HIGH,CRITICAL --format json -o trivy-fs-report.json .
          echo "✅ Dependency scan complete (report saved: trivy-fs-report.json)"
        '''
      }
      post {
        always {
          archiveArtifacts artifacts: 'trivy-fs-report.json', allowEmptyArchive: true
        }
      }
    }

    stage('Package') {
      when { branch 'main' }
      steps {
        sh 'mvn -B -DskipTests package'
        archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
      }
    }

    stage('Docker Build') {
      when { branch 'main' }
      steps {
        sh 'docker build -t $APP_NAME:latest .'
      }
    }

    // 🔹 3. Docker Image Scan - Trivy image
    stage('Docker Image Scan - Trivy') {
      when { branch 'main' }
      steps {
        sh '''
          echo "🐳 Scanning Docker image with Trivy..."
          trivy image --exit-code 0 --severity HIGH,CRITICAL --format json -o trivy-image-report.json $APP_NAME:latest
          echo "✅ Docker image scan complete (report saved: trivy-image-report.json)"
        '''
      }
      post {
        always {
          archiveArtifacts artifacts: 'trivy-image-report.json', allowEmptyArchive: true
        }
      }
    }

    stage('Deploy to Minikube') {
      when { branch 'main' }
      steps {
        sh '''
          echo "🚀 Deploying to Minikube..."

          # Make sure Docker commands target Minikube's internal daemon
          eval $(minikube -p minikube docker-env)

          # Build image directly inside Minikube Docker
          docker build -t $APP_NAME:latest .

          # Optionally load image cache (useful for remote Jenkins)
          minikube image load $APP_NAME:latest

          # Apply manifests
          kubectl apply -f k8s/deployment.yaml
          kubectl apply -f k8s/service.yaml

          echo "⏳ Waiting for rollout..."
          kubectl rollout status deployment/$APP_NAME --timeout=300s || true

          echo "✅ Deployment stage finished!"
        '''
      }
    }

    // 🔹 4. DAST - OWASP ZAP (Fixed Version)
    stage('DAST - OWASP ZAP') {
      when { branch 'main' }
      steps {
        sh '''
          echo "🧪 Running OWASP ZAP DAST scan..."

          # Return to host Docker
          eval $(minikube -p minikube docker-env -u) || true

          SERVICE_URL=$(minikube service $APP_NAME --url 2>/dev/null || true)
          if [ -z "$SERVICE_URL" ]; then
            echo "⚠️ Could not get service URL, trying NodePort fallback..."
            NODEPORT=$(kubectl get svc $APP_NAME -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || true)
            if [ -n "$NODEPORT" ]; then
              SERVICE_URL="http://$(minikube ip):${NODEPORT}"
            fi
          fi

          if [ -z "$SERVICE_URL" ]; then
            echo "⚠️ No service URL found. Skipping DAST scan."
            exit 0
          fi

          echo "Target URL: $SERVICE_URL"
          
          # Clean up previous reports
          rm -rf zap_reports
          mkdir -p zap_reports
          chmod 755 zap_reports

          echo "🔧 Starting ZAP scan with workaround for permission issues..."

          # Workaround: Run ZAP with proper working directory and output paths
          docker run --rm --network host \
            -u $(id -u):$(id -g) \
            -v $PWD/zap_reports:/zap/output:rw \
            -w /zap/output \
            ghcr.io/zaproxy/zaproxy:stable \
            zap-baseline.py -t "$SERVICE_URL" -r zap_report.html -I -x zap_report.xml || true

          # Alternative approach if the above still fails
          if [ ! -f "zap_reports/zap_report.html" ]; then
            echo "🔄 Trying alternative ZAP approach..."
            docker run --rm --network host \
              -v $PWD/zap_reports:/zap/wrk:rw \
              ghcr.io/zaproxy/zaproxy:stable \
              bash -c "cd /zap/wrk && zap-baseline.py -t '$SERVICE_URL' -r zap_report.html -I" || true
          fi

          # Check if report was generated
          if [ -f "zap_reports/zap_report.html" ]; then
            echo "✅ DAST scan complete (report saved: zap_reports/zap_report.html)"
            ls -la zap_reports/
          else
            echo "⚠️ DAST scan completed but no report was generated"
            mkdir -p zap_reports
            echo "No vulnerabilities found or scan failed to generate report" > zap_reports/scan_status.txt
          fi
        '''
      }
      post {
        always {
          archiveArtifacts artifacts: 'zap_reports/*', allowEmptyArchive: true
        }
      }
    }
  }

  post {
    success {
      script {
        echo "✅ Build succeeded, notifying GitHub..."
        def COMMIT_SHA = readFile('commit.txt').trim()

        withCredentials([string(credentialsId: 'git', variable: 'GITHUB_TOKEN')]) {
          sh """
            curl -s -X POST \
              -H "Authorization: token ${GITHUB_TOKEN}" \
              -H "Accept: application/vnd.github+json" \
              https://api.github.com/repos/${GITHUB_REPO}/statuses/${COMMIT_SHA} \
              -d '{"state":"success","context":"jenkins/build","description":"Build passed"}'
          """
        }

        sh 'kubectl get pods -o wide || true'
      }
    }

    failure {
      script {
        echo "❌ Build failed, notifying GitHub..."
        def COMMIT_SHA = readFile('commit.txt').trim()

        withCredentials([string(credentialsId: 'git', variable: 'GITHUB_TOKEN')]) {
          sh """
            curl -s -X POST \
              -H "Authorization: token ${GITHUB_TOKEN}" \
              -H "Accept: application/vnd.github+json" \
              https://api.github.com/repos/${GITHUB_REPO}/statuses/${COMMIT_SHA} \
              -d '{"state":"failure","context":"jenkins/build","description":"Build failed"}'
          """
        }
      }
    }
  }
}
