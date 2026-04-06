pipeline {
    agent any

    parameters {
		string(name: 'ROLLBACK_TAG', defaultValue: '', description: 'Optional rollback tag, e.g. build-8'),
        choice(
            name: 'TARGET_ENV',
            choices: ['auto', 'dev', 'prod'],
            description: 'auto=依 branch 自動判斷；也可手動指定 dev / prod'
        )
    }

    environment {
        IMAGE_NAME = 'arkly365/sample-java-app'
        PRIVATE_REGISTRY_IMAGE = 'localhost:5000/sample-java-app'
		IMAGE_TAG  = "build-${BUILD_NUMBER}"
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
                    def branchName = env.BRANCH_NAME ?: 'unknown'
                    env.GIT_BRANCH_NAME = branchName

                    def autoEnv = 'dev'
                    if (branchName == 'main' || branchName == 'master') {
                        autoEnv = 'prod'
                    } else if (branchName == 'develop') {
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
                    env.IMAGE_LATEST_TAG = "${env.EFFECTIVE_ENV}-latest"
					env.SAFE_BRANCH_NAME = (env.BRANCH_NAME ?: 'unknown').replaceAll('[^A-Za-z0-9_.-]', '-')
					env.TRIVY_REPORT_CONTAINER = "trivy-report-${env.SAFE_BRANCH_NAME}-${env.BUILD_NUMBER}"
					env.TRIVY_GATE_CONTAINER = "trivy-gate-${env.SAFE_BRANCH_NAME}-${env.BUILD_NUMBER}"

                    echo "BRANCH_NAME = ${env.BRANCH_NAME}"
                    echo "GIT_BRANCH_NAME = ${env.GIT_BRANCH_NAME}"
                    echo "AUTO_ENV = ${env.AUTO_ENV}"
                    echo "EFFECTIVE_ENV = ${env.EFFECTIVE_ENV}"
                    echo "APP_PORT = ${env.APP_PORT}"
                    echo "CONTAINER_NAME = ${env.CONTAINER_NAME}"
                    echo "IMAGE_TAG = ${env.IMAGE_TAG}"
                    echo "IMAGE_LATEST_TAG = ${env.IMAGE_LATEST_TAG}"
					echo "SAFE_BRANCH_NAME = ${env.SAFE_BRANCH_NAME}"
					echo "TRIVY_REPORT_CONTAINER = ${env.TRIVY_REPORT_CONTAINER}"
					echo "TRIVY_GATE_CONTAINER = ${env.TRIVY_GATE_CONTAINER}"
                }
            }
        }

        stage('Show Build Parameters') {
            steps {
                echo "TARGET_ENV = ${params.TARGET_ENV}"
                echo "BRANCH_NAME = ${env.BRANCH_NAME}"
                echo "GIT_BRANCH_NAME = ${env.GIT_BRANCH_NAME}"
                echo "AUTO_ENV = ${env.AUTO_ENV}"
                echo "EFFECTIVE_ENV = ${env.EFFECTIVE_ENV}"
                echo "IMAGE_TAG = ${env.IMAGE_TAG}"
                echo "IMAGE_LATEST_TAG = ${env.IMAGE_LATEST_TAG}"
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

		/*
        stage('Quality Gate') {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
		*/

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
					docker rm -f ${TRIVY_REPORT_CONTAINER} || true

					docker run -d --name ${TRIVY_REPORT_CONTAINER} \
					  --entrypoint sh \
					  -v trivy_cache:/root/.cache/ \
					  aquasec/trivy:0.62.0 \
					  -c "sleep 300"

					docker cp sample-java-app-build-${BUILD_NUMBER}.tar \
					  ${TRIVY_REPORT_CONTAINER}:/tmp/sample-java-app-build-${BUILD_NUMBER}.tar

					docker exec ${TRIVY_REPORT_CONTAINER} trivy image \
					  --input /tmp/sample-java-app-build-${BUILD_NUMBER}.tar \
					  --scanners vuln \
					  --severity HIGH,CRITICAL \
					  --ignore-unfixed \
					  --no-progress \
					  --format table \
					  > trivy-image-report.txt

					test -f trivy-image-report.txt
					docker rm -f ${TRIVY_REPORT_CONTAINER} || true
				'''
			}
		}

		stage('Trivy Security Gate') {
			steps {
				sh '''
					docker rm -f ${TRIVY_GATE_CONTAINER} || true

					docker run -d --name ${TRIVY_GATE_CONTAINER} \
					  --entrypoint sh \
					  -v trivy_cache:/root/.cache/ \
					  aquasec/trivy:0.62.0 \
					  -c "sleep 300"

					docker cp sample-java-app-build-${BUILD_NUMBER}.tar \
					  ${TRIVY_GATE_CONTAINER}:/tmp/sample-java-app-build-${BUILD_NUMBER}.tar

					docker exec ${TRIVY_GATE_CONTAINER} trivy image \
					  --input /tmp/sample-java-app-build-${BUILD_NUMBER}.tar \
					  --scanners vuln \
					  --severity HIGH,CRITICAL \
					  --ignore-unfixed \
					  --no-progress \
					  --exit-code 0

					docker rm -f ${TRIVY_GATE_CONTAINER} || true
				'''
			}
		}

		/*
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
                sh 'docker tag sample-java-app:build-${BUILD_NUMBER} ${IMAGE_NAME}:${IMAGE_LATEST_TAG}'
            }
        }

        stage('Docker Hub Push') {
            steps {
                sh 'docker push ${IMAGE_NAME}:${IMAGE_TAG}'
                sh 'docker push ${IMAGE_NAME}:${EFFECTIVE_ENV}-latest'
            }
        }
		*/
		
		stage('Push to Private Registry') {
			steps {
				script {
					def stableTag = (env.BRANCH_NAME == 'master') ? 'master-latest' : 'develop-latest'

					sh """
						docker tag sample-java-app:build-${BUILD_NUMBER} ${PRIVATE_REGISTRY_IMAGE}:build-${BUILD_NUMBER}
						docker tag sample-java-app:build-${BUILD_NUMBER} ${PRIVATE_REGISTRY_IMAGE}:${stableTag}

						docker push ${PRIVATE_REGISTRY_IMAGE}:build-${BUILD_NUMBER}
						docker push ${PRIVATE_REGISTRY_IMAGE}:${stableTag}
					"""
				}
			}
		}
		

		/*
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
		*/

<<<<<<< HEAD
        stage('Verify Deployment') {
            steps {
                sh '''
                    sleep 10
                    curl -f http://host.docker.internal:${APP_PORT}/hello
                '''
            }
        }
=======
		stage('Deploy with Docker Compose') {
			steps {
				script {
					def deployTag = ''
					if (params.ROLLBACK_TAG?.trim()) {
						deployTag = params.ROLLBACK_TAG.trim()
					} else {
						deployTag = (env.BRANCH_NAME == 'master') ? 'master-latest' : 'develop-latest'
					}

					echo "Deploy tag = ${deployTag}"

					if (env.BRANCH_NAME == 'master') {
						sh """
							export IMAGE_TAG=${deployTag}
							docker compose -f deploy/docker-compose.prod.yml up -d
						"""
					} else if (env.BRANCH_NAME == 'develop') {
						sh """
							export IMAGE_TAG=${deployTag}
							docker compose -f deploy/docker-compose.dev.yml up -d
						"""
					} else {
						echo "Skip deployment for branch: ${env.BRANCH_NAME}"
					}
				}
			}
		}
		
		
		stage('Verify Deployment') {
			steps {
				script {
					if (env.BRANCH_NAME == 'master') {
						sh '''
							sleep 10
							curl -f http://host.docker.internal:8082/hello
						'''
					} else if (env.BRANCH_NAME == 'develop') {
						sh '''
							sleep 10
							curl -f http://host.docker.internal:8081/hello
						'''
					} else {
						echo "Skip verify for branch: ${env.BRANCH_NAME}"
					}
				}
			}
		}
>>>>>>> master
		
    }

	post {
		always {
			archiveArtifacts artifacts: 'trivy-image-report.txt', fingerprint: true, allowEmptyArchive: true
		}
	}
}