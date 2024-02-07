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
            /*stage('Sonar scan') {
                steps {
                    sh '''
                        sonar-scanner
                    '''
                }
            }*/
            stage('Build') {
                steps {
                    sh '''
                        ls -la
                        zip -q -r ${configMap.component}.zip ./* -x ".git" -x "*.zip"
                        ls -ltr
                    ''' 
                }
            }
            stage('upload artifact') { //Nexus Artifact Uploader plugin
                steps {
                    nexusArtifactUploader(
                        nexusVersion: 'nexus3',
                        protocol: 'http',
                        nexusUrl: pipelineGlobals.nexusURL(), // Using pipeline Globals
                        //nexusUrl: "${env.nexusURL}",
                        //nexusUrl: "${params.nexusURL}",
                        groupId: 'com.roboshop',
                        version: "${packageVersion}", //Updates nexus repository with new sematic version
                        repository: "${configMap.component}",
                        credentialsId: 'nexus-auth', //Congifure inside manage credentials section
                        artifacts: [
                            [artifactId: "${configMap.component}",
                            classifier: '',
                            file: "${configMap.component}.zip",
                            type: 'zip']
                        ]
                    )
                }
            }
            stage('Trigger-deploy-job') {
                when {
                    expression {
                        params.Deploy
                    }
                }
                steps {
                    script {
                        def params = [
                            string(name: 'version', value: "${packageVersion}"),
                            string(name: 'environment', value: "${environment}")
                        ]
                        build job: "../${configMap.component}-deploy", wait: true, parameters:params //triggers app component job
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