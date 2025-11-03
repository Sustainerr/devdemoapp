pipeline {
  agent any
  options { skipStagesAfterUnstable() }

  tools { maven 'Maven3' }

  environment {
    APP_NAME = 'demoapp'
    PORT     = '8081'
    GITHUB_REPO = 'Sustainerr/devdemoapp'      // owner/repo
    GITHUB_TOKEN = credentials('jenkin')       // your GitHub token credential
    SONARQUBE = credentials('sonar')           // your SonarQube token credential (ID = sonar)
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        echo "Building branch: ${env.BRANCH_NAME}"

        script {
          // Set GitHub commit status: PENDING
          def sha = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
          sh """
            curl -s -X POST \
              -H "Authorization: token ${GITHUB_TOKEN}" \
              -H "Accept: application/vnd.github+json" \
              https://api.github.com/repos/${GITHUB_REPO}/statuses/${sha} \
              -d '{"state":"pending","context":"jenkins/build","description":"Build started"}'
          """
        }
      }
    }

    stage('Compile') {
      steps {
        sh 'mvn -B -Dspotbugs.skip=true -Ddependency-check.skip=true clean compile'
      }
    }

    stage('Test') {
      steps { sh 'mvn -B test' }
      post { always { junit 'target/surefire-reports/*.xml' } }
    }

    // 🔹 NEW: SonarQube Static Analysis Stage
    stage('SAST - SonarQube Analysis') {
      steps {
        withSonarQubeEnv('SonarQube') {
          sh '''
            mvn clean verify sonar:sonar \
              -Dsonar.projectKey=devdemoapp \
              -Dsonar.host.url=http://localhost:9000 \
              -Dsonar.login=$SONARQUBE
          '''
        }
      }
    }

    // 🔹 NEW: Optional Quality Gate Stage
    stage('Quality Gate') {
      steps {
        script {
          timeout(time: 3, unit: 'MINUTES') {
            def qg = waitForQualityGate()
            if (qg.status != 'OK') {
              error "Pipeline aborted due to SonarQube quality gate failure: ${qg.status}"
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
      steps { sh 'docker build -t $APP_NAME:latest .' }
    }

    stage('Docker Run') {
      when { branch 'main' }
      steps {
        sh 'docker rm -f $APP_NAME || true'
        sh 'docker run -d --name $APP_NAME -p $PORT:$PORT $APP_NAME:latest'
      }
    }
  }

  post {
    success {
      script {
        def sha = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
        sh """
          curl -s -X POST \
            -H "Authorization: token ${GITHUB_TOKEN}" \
            -H "Accept: application/vnd.github+json" \
            https://api.github.com/repos/${GITHUB_REPO}/statuses/${sha} \
            -d '{"state":"success","context":"jenkins/build","description":"Build passed"}'
        """
      }
    }
    failure {
      script {
        def sha = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
        sh """
          curl -s -X POST \
            -H "Authorization: token ${GITHUB_TOKEN}" \
            -H "Accept: application/vnd.github+json" \
            https://api.github.com/repos/${GITHUB_REPO}/statuses/${sha} \
            -d '{"state":"failure","context":"jenkins/build","description":"Build failed"}'
        """
      }
    }
    always {
      sh 'docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}" || true'
    }
  }
}

