pipeline {
    agent { label 'ubuntu-docker' }

    environment {
        SPRING_PROFILES_ACTIVE = 'ci'
    }

    tools { 
        maven 'maven 3.9.9'
    }

    stages {
        stage('Compile') {
            steps {
                echo 'Compiling code...'
                sh 'mvn clean compile -B' 
            }
        }

        stage('Test') {
            steps {
                echo 'Running All Tests (Unit + Integration with Testcontainers)...'
                sh 'mvn test -B' 
                // Примечание: В будущем (задача CI-3) мы разделим юнит и интеграционные тесты
                // с помощью Surefire и Failsafe для более гранулярного контроля и отчетности.
            }
        }

        stage('Package') {
            steps {
                echo 'Packaging application...'
                sh 'mvn package -B -DskipTests' 
            }
        }
        
        stage('Archive Results') {
            steps {
                echo 'Archiving results...'
                archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true, onlyIfSuccessful: true, allowEmptyArchive: true
                junit '**/target/surefire-reports/*.xml' 
                // junit '**/target/failsafe-reports/*.xml' 
            }
        }
    }
    
    post {
        always {
            echo 'Pipeline finished. Cleaning workspace...'
            cleanWs() 
        }
        success {
            echo 'Pipeline successful!'
        }
        failure {
            echo 'Pipeline failed!'
        }
    }
}