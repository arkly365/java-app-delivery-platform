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

        stage('SonarQube Env') {
            steps {
                withSonarQubeEnv('sonarqube-local') {
                    echo 'Connected to SonarQube from SCM pipeline'
                }
            }
        }
    }
}