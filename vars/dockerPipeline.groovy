def call(Map config = [:]) {

  if (config.cron) {
    properties([
      pipelineTriggers([
        cron(config.cron)
      ])
    ])
  }
  
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
            image 'docker:26-cli'
            args '-v /var/run/docker.sock:/var/run/docker.sock -v ${WORKSPACE}:/workspace -w /workspace -u root' // This is kinda hacky and not an optimal way of doing it, it's a quick fix for DinD issues
            reuseNode true
          }
        }
        steps {
          sh """
            git config --global safe.directory /workspace || true
            docker build -t ${IMAGE_BASE}:${TAG} -t ${IMAGE_BASE}:latest .
            
            if [ \$(( \$(date +%s) % 5 )) -eq 0 ]; then
              echo "Simulated failure" 
              exit 1
            fi
          """ // this simulates a flaky pipeline with a 20% chance of failure
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
