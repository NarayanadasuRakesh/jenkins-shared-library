def call(Map configMap) {

    pipeline {
        agent {
            node { label 'node1' }
        }
        environment {
            packageVersion = ''
            nexusURL = credentials('nexusURL')
            environment = 'dev'
        }
        options {
            ansiColor('xterm') //ansiColor plugin
        }
        parameters {
            //string(name:'nexusURL', defaultValue: '')
            booleanParam(name: 'Deploy', defaultValue: false)
        }
        stages {
            stage('Get the version') {
                steps {
                    sh 'ls -la'
                    script { //Pipeline utility steps plugin
                        def packageJson = readJSON file: 'package.json'
                        packageVersion = packageJson.version
                        echo "Application version: $packageVersion"
                    }
                }
            }
            stage('Install dependencies') {
                steps {     //Install nodejs in agent/node
                    sh '''
                        ls -la
                        npm install
                    '''
                }
            }
            stage('Unit tests') {
                steps {
                    sh '''
                        echo "Unit tests will run here"
                    '''
                }
            }
            stage('Sonar scan') {
                steps {
                    sh '''
                        sonar-scanner
                    '''
                }
            }
            stage('Build') {
                steps {
                    sh '''
                        ls -la
                        zip -q -r catalogue.zip ./* -x ".git" -x "*.zip"
                        ls -ltr
                    ''' 
                }
            }
            stage('upload artifact') { //Nexus Artifact Uploader plugin
                steps {
                    nexusArtifactUploader(
                        nexusVersion: 'nexus3',
                        protocol: 'http',
                        nexusUrl: "${env.nexusURL}",
                        //nexusUrl: "${params.nexusURL}",
                        groupId: 'com.roboshop',
                        version: "${packageVersion}", //Updates nexus repository with new sematic version
                        repository: 'catalogue',
                        credentialsId: 'nexus-auth', //Congifure inside manage credentials section
                        artifacts: [
                            [artifactId: 'catalogue',
                            classifier: '',
                            file: 'catalogue.zip',
                            type: 'zip']
                        ]
                    )
                }
            }
            stage('Trigger-deploy-job') {
                when {
                    expression {
                        "${params.Deploy}" == 'true'
                    }
                }
                steps {
                    script {
                        def params = [
                            string(name: 'version', value: "${packageVersion}"),
                            string(name: 'environment', value: "${environment}")
                        ]
                        build job: "catalogue-deploy", wait: true, parameters:params //triggers catalogue-deploy job
                    }
                }
            }
        }
        post {
            always {
                echo "Deleting directory"
                deleteDir()
            }
        }
    }
}