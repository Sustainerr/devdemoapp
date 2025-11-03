pipeline {
  agent any
  options { skipStagesAfterUnstable() }

  tools { maven 'Maven3' }

  environment {
    APP_NAME = 'demoapp'
    PORT     = '8081'
    GITHUB_REPO = 'Sustainerr/devdemoapp'
    GITHUB_TOKEN = credentials('jenkin')
    SONARQUBE = credentials('sonar')
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        echo "Building branch: ${env.BRANCH_NAME}"
      }
    }

    stage('Set GitHub Status - Pending') {
      steps {
        script {
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
          sh '''
            mvn clean verify sonar:sonar \
              -Dsonar.projectKey=devdemoapp \
              -Dsonar.host.url=http://localhost:9000 \
              -Dsonar.login=$SONARQUBE
          '''
        }
      }
    }

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
      steps { 
        sh 'docker build -t $APP_NAME:latest .' 
      }
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
    always {
      script {
        // Only run if we have a workspace context
        if (currentBuild.rawBuild.getExecutor().getCurrentWorkspace() != null) {
          sh 'docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}" || true'
        }
      }
    }
    success {
      script {
        updateGitHubStatus('success', 'Build passed')
      }
    }
    failure {
      script {
        updateGitHubStatus('failure', 'Build failed')
      }
    }
    aborted {
      script {
        updateGitHubStatus('error', 'Build aborted')
      }
    }
  }
}

// Define function to update GitHub status
def updateGitHubStatus(String state, String description) {
  try {
    // Get the SHA in a way that doesn't require workspace
    def sha = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
    sh """
      curl -s -X POST \
        -H "Authorization: token ${env.GITHUB_TOKEN}" \
        -H "Accept: application/vnd.github+json" \
        https://api.github.com/repos/${env.GITHUB_REPO}/statuses/${sha} \
        -d '{"state":"${state}","context":"jenkins/build","description":"${description}"}'
    """
  } catch (Exception e) {
    echo "Failed to update GitHub status: ${e.message}"
  }
}
