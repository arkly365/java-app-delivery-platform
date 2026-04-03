pipeline {
    agent any

    environment {
        IMAGE_NAME = 'arkly365/sample-java-app'
        IMAGE_TAG  = "build-${BUILD_NUMBER}"
    }

    stages {
        stage('Init') {
            steps {
                echo 'Pipeline from SCM started'
            }
        }

        stage('Check Tools') {
            steps {
                sh 'git --version'
                sh 'mvn -version'
                sh 'docker --version'
            }
        }

        stage('Maven Test') {
            steps {
                sh 'mvn clean test'
            }
        }

        stage('SonarQube Scan') {
            steps {
                withSonarQubeEnv('sonarqube-local') {
                    sh '''
                        mvn sonar:sonar \
                          -Dsonar.projectKey=sample-java-app \
                          -Dsonar.projectName=sample-java-app
                    '''
                }
            }
        }

        stage('Maven Package') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t sample-java-app:build-${BUILD_NUMBER} .'
            }
        }

        stage('Trivy Image Scan') {
            steps {
                sh '''
                    docker run --rm \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      -v trivy_cache:/root/.cache/ \
                      -v "$WORKSPACE:/work" \
                      aquasec/trivy:0.62.0 image \
                      --severity HIGH,CRITICAL \
                      --ignore-unfixed \
                      --no-progress \
                      --format table \
                      --output /work/trivy-image-report.txt \
                      --exit-code 0 \
                      sample-java-app:build-${BUILD_NUMBER}
                '''
            }
        }

        stage('Docker Login') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-creds',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'
                }
            }
        }

        stage('Docker Tag') {
            steps {
                sh 'docker tag sample-java-app:build-${BUILD_NUMBER} ${IMAGE_NAME}:${IMAGE_TAG}'
                sh 'docker tag sample-java-app:build-${BUILD_NUMBER} ${IMAGE_NAME}:latest'
            }
        }

        stage('Docker Push') {
            steps {
                sh 'docker push ${IMAGE_NAME}:${IMAGE_TAG}'
                sh 'docker push ${IMAGE_NAME}:latest'
            }
        }
		
		stage('Push to Private Registry') {
			steps {
				sh '''
					docker tag sample-java-app:build-${BUILD_NUMBER} \
					  localhost:5000/sample-java-app:build-${BUILD_NUMBER}

					docker tag sample-java-app:build-${BUILD_NUMBER} \
					  localhost:5000/sample-java-app:latest

					docker push localhost:5000/sample-java-app:build-${BUILD_NUMBER}
					docker push localhost:5000/sample-java-app:latest
				'''
			}
		}
		
    }

    post {
        always {
            archiveArtifacts artifacts: 'trivy-image-report.txt', fingerprint: true
        }
    }
}