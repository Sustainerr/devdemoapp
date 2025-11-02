pipeline {
  agent any
  options { skipStagesAfterUnstable() }

  tools { maven 'Maven3' }   // must match your Jenkins tool name

  environment {
    APP_NAME = 'demoapp'
    PORT     = '8081'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        echo "Building branch: ${env.BRANCH_NAME}"
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
      when { branch 'main' }     // only on main
      steps {
        sh 'mvn -B -DskipTests package'
        archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
      }
    }

    stage('Docker Build') {
      when { branch 'main' }     // only on main
      steps { sh 'docker build -t $APP_NAME:latest .' }
    }

    stage('Docker Run') {
      when { branch 'main' }     // only on main
      steps {
        sh 'docker rm -f $APP_NAME || true'
        sh 'docker run -d --name $APP_NAME -p $PORT:$PORT $APP_NAME:latest'
      }
    }
  }

  post {
    always {
      sh 'docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}" || true'
    }
  }
}

