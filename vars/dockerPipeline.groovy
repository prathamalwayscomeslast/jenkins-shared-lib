def call() {
  pipeline {
    agent any

    environment {
      IMAGE_BASE = "prathamalwayscomeslast/${env.JOB_NAME.toLowerCase()}"
      TAG        = "${env.BUILD_NUMBER}"
    }

    stages {
      stage('Checkout') {
        steps {
          checkout scm
        }
      }

      stage('Docker Build') {
        agent {
          docker {
            image 'docker:26-dind'
            args '-v ${WORKSPACE}:/workspace -w /workspace --privileged -u root --entrypoint=""'
            reuseNode true
          }
        }
        steps {
          sh """
            git config --global safe.directory /workspace || true
            docker build -t ${IMAGE_BASE}:${TAG} -t ${IMAGE_BASE}:latest .
          """
        }
      }

      stage('Push DockerHub') {
        steps {
          withCredentials([usernamePassword(
            credentialsId: 'dockerhub-creds',
            usernameVariable: 'DH_USER',
            passwordVariable: 'DH_PASS'
          )]) {
            sh """
              echo \$DH_PASS | docker login -u \$DH_USER --password-stdin
              docker push ${IMAGE_BASE}:${TAG}
              docker push ${IMAGE_BASE}:latest
            """
          }
        }
      }
    }
  }
}
