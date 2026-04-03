pipeline {
    agent any

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
		
		stage('Check Dockerfile') {
			steps {
				sh 'pwd'
				sh 'ls -la'
				sh 'echo "===== Dockerfile ====="'
				sh 'cat Dockerfile'
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
					  aquasec/trivy:0.62.0 image \
					  --severity MEDIUM,HIGH,CRITICAL \
					  --no-progress \
					  --format table \
					  --exit-code 1 \
					  sample-java-app:build-${BUILD_NUMBER} \
					  > trivy-image-report.txt
				'''
				sh 'ls -la'
				sh 'test -f trivy-image-report.txt'
			}
		}
    }

	post {
		always {
			archiveArtifacts artifacts: 'trivy-image-report.txt', fingerprint: true
		}
	}
}