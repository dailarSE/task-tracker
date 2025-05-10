pipeline {
    agent { label 'ubuntu-docker' }

    environment {
        SPRING_PROFILES_ACTIVE = 'ci'
    }

    tools {
        maven 'maven 3.9.9'
    }

    stages {
        stage('Build, Test & Verify') {
            steps {
                script {
                    echo "Running Maven build, all tests (unit & integration), and generating coverage report..."
                    sh "mvn clean verify -B -Duser.timezone=UTC"
                }
            }
        }

        stage('Archive & Publish Test Reports') {
            steps {
                script {
                    echo 'Archiving JAR artifacts...'
                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true, allowEmptyArchive: true

                    echo "Archiving Maven Surefire (Unit Test) reports..."
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true

                    echo "Archiving Maven Failsafe (Integration Test) reports..."
                    junit testResults: '**/target/failsafe-reports/*.xml', allowEmptyResults: true
                }
            }
        }
    }

    post {
        always {
            echo 'Pipeline finished. Cleaning workspace...'
            cleanWs()
        }
        success {
            script {
                echo 'Pipeline successful! Publishing Code Coverage report (JaCoCo)...'
                recordCoverage(
                        tools: [
                                [parser: 'JACOCO', pattern: '**/target/site/jacoco/jacoco.xml']
                        ],
                        id: 'jacocoCoverage',
                        name: 'JaCoCo Code Coverage',
                        sourceCodeRetention: 'LAST_BUILD'
                )
            }
        }
        failure {
            echo 'Pipeline failed!'
        }
    }
}