pipeline {
    agent any

    environment {
        IMAGE_NAME = 'arkly365/sample-java-app'
        IMAGE_TAG = "build-${BUILD_NUMBER}"
        PRIVATE_REGISTRY_IMAGE = "localhost:5000/sample-java-app"
        CONTAINER_NAME = 'sample-java-app-deploy'
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

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
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

        stage('Trivy Report') {
            steps {
                sh '''
                    docker run --rm \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      -v trivy_cache:/root/.cache/ \
                      -v "$WORKSPACE:/work" \
                      aquasec/trivy:0.62.0 image \
                      --scanners vuln \
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

        stage('Docker Hub Login') {
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

        stage('Docker Hub Tag') {
            steps {
                sh 'docker tag sample-java-app:build-${BUILD_NUMBER} ${IMAGE_NAME}:${IMAGE_TAG}'
                sh 'docker tag sample-java-app:build-${BUILD_NUMBER} ${IMAGE_NAME}:latest'
            }
        }

        stage('Docker Hub Push') {
            steps {
                sh 'docker push ${IMAGE_NAME}:${IMAGE_TAG}'
                sh 'docker push ${IMAGE_NAME}:latest'
            }
        }

        stage('Push to Private Registry') {
            steps {
                sh '''
                    docker tag sample-java-app:build-${BUILD_NUMBER} \
                      ${PRIVATE_REGISTRY_IMAGE}:build-${BUILD_NUMBER}

                    docker tag sample-java-app:build-${BUILD_NUMBER} \
                      ${PRIVATE_REGISTRY_IMAGE}:latest

                    docker push ${PRIVATE_REGISTRY_IMAGE}:build-${BUILD_NUMBER}
                    docker push ${PRIVATE_REGISTRY_IMAGE}:latest
                '''
            }
        }

        stage('Deploy from Private Registry') {
            steps {
                sh '''
                    echo "Checking containers using port 8081..."
                    OLD_CONTAINERS=$(docker ps -q --filter "publish=8081")
                    if [ -n "$OLD_CONTAINERS" ]; then
                      echo "Removing containers using port 8081: $OLD_CONTAINERS"
                      docker rm -f $OLD_CONTAINERS
                    else
                      echo "No containers are using port 8081"
                    fi

                    docker rm -f ${CONTAINER_NAME} || true

                    docker run -d \
                      --name ${CONTAINER_NAME} \
                      -p 8081:8080 \
                      ${PRIVATE_REGISTRY_IMAGE}:build-${BUILD_NUMBER}
                '''
            }
        }

        stage('Verify Deployment') {
            steps {
                sh '''
                    sleep 10
                    curl -f http://host.docker.internal:8081/hello
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