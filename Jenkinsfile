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

