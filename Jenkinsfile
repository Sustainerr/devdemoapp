pipeline {
  agent any
  options { skipStagesAfterUnstable() }

  tools { maven 'Maven3' }

  environment {
    APP_NAME = 'demoapp'
    PORT     = '8081'
    GITHUB_REPO = 'Sustainerr/devdemoapp'
    GITHUB_TOKEN = credentials('jenkin')
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        echo "Building branch: ${env.BRANCH_NAME}"

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
      steps { sh 'mvn -B test' }
      post { always { junit 'target/surefire-reports/*.xml' } }
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

    stage('Deploy to Minikube') {
      when { branch 'main' }
      steps {
        sh '''
          echo "Deploying to Minikube..."
          eval $(minikube -p minikube docker-env)
          docker build -t $APP_NAME:latest .
          kubectl apply -f k8s/deployment.yaml
          kubectl apply -f k8s/service.yaml
          kubectl rollout status deployment/$APP_NAME --timeout=120s
          echo "Deployment successful!"
        '''
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
      sh 'kubectl get pods -o wide || true'
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
  }
}

