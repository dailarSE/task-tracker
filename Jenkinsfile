pipeline {
    agent { label 'build-in-agent' }
    environment {
        SPRING_PROFILES_ACTIVE = 'ci'
    }
	tools { maven 'maven 3.9.9' }

    stages {
        stage('Build') {
            steps {
                sh 'mvn clean install -B -DskipTests -Dspring-boot.repackage.skip=true'
            }
        }
        stage('Test') {
            steps {
                echo 'Testing..'
            }
        }
        stage('Deploy') {
            steps {
                echo 'Deploying....'
            }
        }
    }
	
	post {
        success {
            echo 'Build successful!'
        }
        failure {
            echo 'Build failed!'
        }
    }
}