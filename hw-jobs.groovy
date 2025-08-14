String appName = 'hw-flask-app'
String proxyName = 'hw-proxy'

String releaseTag = 'test'

createDockerBuildJob(appName, releaseTag)
createDockerBuildJob(proxyName, releaseTag)
createIntegrationTestJob(appName, proxyName, releaseTag, 'hw', '8888')

void createDockerBuildJob(String projectName, String imageTag) {
    pipelineJob(projectName + '-build') {
        definition {
            cps {
                script("""
                pipeline {
                    agent any
                    environment {
                        IMAGE_NAME = 'michaelchernov/${projectName}'
                        IMAGE_TAG = '${imageTag}'
                    }
                    stages {
                        stage('Clone Repository') {
                            steps {
                                echo 'Cloning the source code...'
                                git branch: 'main',
                                    url: 'https://github.com/MichaelChernov/${projectName}.git',
                                    credentialsId: 'github-creds'
                            }
                        }
                        stage('Build and Push Image') {
                            steps {
                                script {
                                    echo "Building Docker image..."
                                    def outputImage = docker.build("\${env.IMAGE_NAME}:\${env.IMAGE_TAG}")

                                    echo "Pushing image to registry..."
                                    docker.withRegistry("", "docker-creds") {
                                        outputImage.push()
                                    }
                                }
                            }
                        }
                    }
                }
                """.stripIndent())
                sandbox()
            }
        }
    }
}

void createIntegrationTestJob(String appName, String proxyName, String imageTag, String networkName, String hostPort) {
    pipelineJob('Integration-Test') {
        definition {
            cps {
                script("""
                    //Global variables to stop the containers in post
                    def appContainer = null
                    def proxyContainer = null

                    pipeline {
                        agent any

                        environment {
                            IMAGE_TAG = '${imageTag}'
                        }

                        stages {
                            stage('1. Setup') {
                                steps {
                                    echo "Creating Docker network..."
                                    sh "docker network create ${networkName}"

                                    script {

                                        def appImage = docker.image("michaelchernov/${appName}:\${env.IMAGE_TAG}")
                                        def proxyImage = docker.image("michaelchernov/${proxyName}:\${env.IMAGE_TAG}")

                                        echo "Pulling images from Docker Hub..."
                                        appImage.pull()
                                        proxyImage.pull()

                                        echo "Starting App Container..."
                                        appContainer = appImage.run("--name ${appName} --network ${networkName} -v /var/run/docker.sock:/var/run/docker.sock")

                                        echo "Starting Proxy Container..."
                                        proxyContainer = proxyImage.run("--name ${proxyName} --network ${networkName} -p 127.0.0.1:${hostPort}:80")
                                    }
                                }
                            }

                            stage('2. Verify Proxy Connection') {
                                steps {
                                    echo "Waiting for containers to start..."
                                    sleep 5

                                    echo "Sending request..."
                                    sh "curl --fail http://127.0.0.1:${hostPort}"

                                    echo "Success! Received 200 OK"
                                }
                            }
                        }

                        post {
                            always {

                                //Stops and Deletes running containers
                                script {
                                    if (appContainer != null) { appContainer.stop() }

                                    if (proxyContainer != null) { proxyContainer.stop() }

                                }

                                sh "docker network rm ${networkName} || true"
                                echo "Cleanup complete."
                            }
                        }
                    }
                """.stripIndent())
                sandbox()
            }
        }
    }
}
