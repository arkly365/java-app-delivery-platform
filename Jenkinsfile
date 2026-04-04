pipeline {
    agent any

    parameters {
        choice(
            name: 'TARGET_ENV',
            choices: ['dev', 'prod'],
            description: '選擇本次 build / deploy 的目標環境'
        )
    }

	environment {
        IMAGE_NAME = 'arkly365/sample-java-app'
        IMAGE_TAG  = "build-${BUILD_NUMBER}-${params.TARGET_ENV}"
        PRIVATE_REGISTRY_IMAGE = "localhost:5000/sample-java-app"
        CONTAINER_NAME = "sample-java-app-${params.TARGET_ENV}"
        APP_PORT = "${params.TARGET_ENV == 'prod' ? '8082' : '8081'}"
    }

    stages {
        stage('Init') {
            steps {
                echo 'Pipeline from SCM started'
            }
        }
		
		stage('Show Build Parameters') {
            steps {
                echo "TARGET_ENV = ${params.TARGET_ENV}"
                echo "IMAGE_TAG  = ${env.IMAGE_TAG}"
                echo "CONTAINER_NAME = ${env.CONTAINER_NAME}"
				echo "APP_PORT = ${env.APP_PORT}"
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
                      ${PRIVATE_REGISTRY_IMAGE}:${IMAGE_TAG}

                    docker tag sample-java-app:build-${BUILD_NUMBER} \
                      ${PRIVATE_REGISTRY_IMAGE}:${TARGET_ENV}-latest

                    docker push ${PRIVATE_REGISTRY_IMAGE}:${IMAGE_TAG}
                    docker push ${PRIVATE_REGISTRY_IMAGE}:${TARGET_ENV}-latest
                '''
            }
        }
		
		stage('Deploy from Private Registry') {
            steps {
                sh '''
                    docker rm -f ${CONTAINER_NAME} || true

                    docker run -d \
                      --name ${CONTAINER_NAME} \
                      -p ${APP_PORT}:8080 \
                      ${PRIVATE_REGISTRY_IMAGE}:${IMAGE_TAG}
                '''
            }
        }
		
		stage('Verify Deployment') {
            steps {
                sh '''
                    sleep 10
                    curl -f http://host.docker.internal:${APP_PORT}/hello
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