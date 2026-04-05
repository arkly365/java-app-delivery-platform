pipeline {
    agent any

    parameters {
        choice(
            name: 'TARGET_ENV',
            choices: ['auto', 'dev', 'prod'],
            description: 'auto=依 branch 自動判斷；也可手動指定 dev / prod'
        )
    }

    environment {
        IMAGE_NAME = 'arkly365/sample-java-app'
        PRIVATE_REGISTRY_IMAGE = 'localhost:5000/sample-java-app'
    }

    stages {
        stage('Init') {
            steps {
                echo 'Pipeline from SCM started'
            }
        }

        stage('Detect Branch') {
            steps {
                script {
                    def gitBranch = sh(
                        script: 'git rev-parse --abbrev-ref HEAD',
                        returnStdout: true
                    ).trim()

                    env.GIT_BRANCH_NAME = gitBranch

                    def autoEnv = 'dev'
                    if (gitBranch == 'main' || gitBranch == 'master') {
                        autoEnv = 'prod'
                    } else if (gitBranch == 'develop' || gitBranch == 'develop') {
                        autoEnv = 'dev'
                    }

                    env.AUTO_ENV = autoEnv

                    if (params.TARGET_ENV == 'auto') {
                        env.EFFECTIVE_ENV = env.AUTO_ENV
                    } else {
                        env.EFFECTIVE_ENV = params.TARGET_ENV
                    }

                    env.APP_PORT = (env.EFFECTIVE_ENV == 'prod') ? '8082' : '8081'
                    env.CONTAINER_NAME = "sample-java-app-${env.EFFECTIVE_ENV}"
                    env.IMAGE_TAG = "build-${env.BUILD_NUMBER}-${env.EFFECTIVE_ENV}"

                    echo "GIT_BRANCH_NAME = ${env.GIT_BRANCH_NAME}"
                    echo "AUTO_ENV = ${env.AUTO_ENV}"
                    echo "EFFECTIVE_ENV = ${env.EFFECTIVE_ENV}"
                    echo "APP_PORT = ${env.APP_PORT}"
                    echo "CONTAINER_NAME = ${env.CONTAINER_NAME}"
                    echo "IMAGE_TAG = ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Show Build Parameters') {
            steps {
                echo "TARGET_ENV = ${params.TARGET_ENV}"
                echo "GIT_BRANCH_NAME = ${env.GIT_BRANCH_NAME}"
                echo "AUTO_ENV = ${env.AUTO_ENV}"
                echo "EFFECTIVE_ENV = ${env.EFFECTIVE_ENV}"
                echo "IMAGE_TAG = ${env.IMAGE_TAG}"
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
                timeout(time: 2, unit: 'MINUTES') {
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

        stage('Docker Save') {
            steps {
                sh '''
                    docker save sample-java-app:build-${BUILD_NUMBER} \
                      -o sample-java-app-build-${BUILD_NUMBER}.tar
                '''
                sh 'ls -lh sample-java-app-build-${BUILD_NUMBER}.tar'
            }
        }

		stage('Trivy Report') {
			steps {
				sh '''
					docker rm -f trivy-tmp-report || true

					docker run -d --name trivy-tmp-report \
					  --entrypoint sh \
					  -v trivy_cache:/root/.cache/ \
					  aquasec/trivy:0.62.0 \
					  -c "sleep 300"

					docker cp sample-java-app-build-${BUILD_NUMBER}.tar \
					  trivy-tmp-report:/tmp/sample-java-app-build-${BUILD_NUMBER}.tar

					docker exec trivy-tmp-report trivy image \
					  --input /tmp/sample-java-app-build-${BUILD_NUMBER}.tar \
					  --scanners vuln \
					  --severity HIGH,CRITICAL \
					  --ignore-unfixed \
					  --no-progress \
					  --format table \
					  > trivy-image-report.txt

					test -f trivy-image-report.txt
					docker rm -f trivy-tmp-report
				'''
			}
		}

		stage('Trivy Security Gate') {
			steps {
				sh '''
					docker rm -f trivy-tmp-gate || true

					docker run -d --name trivy-tmp-gate \
					  --entrypoint sh \
					  -v trivy_cache:/root/.cache/ \
					  aquasec/trivy:0.62.0 \
					  -c "sleep 300"

					docker cp sample-java-app-build-${BUILD_NUMBER}.tar \
					  trivy-tmp-gate:/tmp/sample-java-app-build-${BUILD_NUMBER}.tar

					docker exec trivy-tmp-gate trivy image \
					  --input /tmp/sample-java-app-build-${BUILD_NUMBER}.tar \
					  --scanners vuln \
					  --severity HIGH,CRITICAL \
					  --ignore-unfixed \
					  --no-progress \
					  --exit-code 0

					docker rm -f trivy-tmp-gate
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
                sh 'docker tag sample-java-app:build-${BUILD_NUMBER} ${IMAGE_NAME}:${EFFECTIVE_ENV}-latest'
            }
        }

        stage('Docker Hub Push') {
            steps {
                sh 'docker push ${IMAGE_NAME}:${IMAGE_TAG}'
                sh 'docker push ${IMAGE_NAME}:${EFFECTIVE_ENV}-latest'
            }
        }

        stage('Push to Private Registry') {
            steps {
                sh '''
                    docker tag sample-java-app:build-${BUILD_NUMBER} \
                      ${PRIVATE_REGISTRY_IMAGE}:${IMAGE_TAG}

                    docker tag sample-java-app:build-${BUILD_NUMBER} \
                      ${PRIVATE_REGISTRY_IMAGE}:${EFFECTIVE_ENV}-latest

                    docker push ${PRIVATE_REGISTRY_IMAGE}:${IMAGE_TAG}
                    docker push ${PRIVATE_REGISTRY_IMAGE}:${EFFECTIVE_ENV}-latest
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