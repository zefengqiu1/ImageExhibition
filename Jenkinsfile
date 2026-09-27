pipeline {
    agent any

    environment {
        SERVER_HOST = '192.210.161.144'
        DOCKERHUB_NAMESPACE = 'zefengqiu1'

        NETWORK_NAME = 'image-exhibition-net'
        BACKEND_CONTAINER = 'backend'
        FRONTEND_CONTAINER = 'frontend'
        MONGO_CONTAINER = 'mongo'
        PROMETHEUS_CONTAINER = 'prometheus'
        GRAFANA_CONTAINER = 'grafana'

        BACKEND_IMAGE = 'zefengqiu1/image-exhibition-backend'
        FRONTEND_IMAGE = 'zefengqiu1/image-exhibition-frontend'
        IMAGE_TAG = "${BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                git credentialsId: 'github-token',
                    url: 'https://github.com/zefengqiu1/ImageExhibition.git',
                    branch: 'movie'
            }
        }

        stage('Build Backend') {
            steps {
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Build Frontend') {
            steps {
                dir('video_exhibition') {
                    sh 'npm ci'
                    sh 'npm run build'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    docker build -t "$BACKEND_IMAGE:$IMAGE_TAG" -t "$BACKEND_IMAGE:latest" .
                    docker build -t "$FRONTEND_IMAGE:$IMAGE_TAG" -t "$FRONTEND_IMAGE:latest" video_exhibition
                '''
            }
        }

        stage('Docker Push') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials',
                    usernameVariable: 'DOCKERHUB_USERNAME',
                    passwordVariable: 'DOCKERHUB_PASSWORD'
                )]) {
                    sh '''
                        echo "$DOCKERHUB_PASSWORD" | docker login -u "$DOCKERHUB_USERNAME" --password-stdin
                        docker push "$BACKEND_IMAGE:$IMAGE_TAG"
                        docker push "$BACKEND_IMAGE:latest"
                        docker push "$FRONTEND_IMAGE:$IMAGE_TAG"
                        docker push "$FRONTEND_IMAGE:latest"
                    '''
                }
            }
        }

        stage('Prepare Runtime') {
            steps {
                sh '''
                    docker network inspect "$NETWORK_NAME" >/dev/null 2>&1 || docker network create "$NETWORK_NAME"

                    docker ps -a --format '{{.Names}}' | grep -qx "$MONGO_CONTAINER" || \\
                    docker run -d \\
                        --name "$MONGO_CONTAINER" \\
                        --network "$NETWORK_NAME" \\
                        -p 27017:27017 \\
                        -v mongo_data:/data/db \\
                        mongo:latest
                    docker start "$MONGO_CONTAINER" >/dev/null 2>&1 || true

                    mkdir -p "$HOME/image-exhibition/prometheus"
                    cp deploy/prometheus/prometheus.yml "$HOME/image-exhibition/prometheus/prometheus.yml"
                '''
            }
        }

        stage('Deploy Application') {
            steps {
                sh '''
                    docker pull "$BACKEND_IMAGE:$IMAGE_TAG"
                    docker pull "$FRONTEND_IMAGE:$IMAGE_TAG"

                    docker rm -f "$BACKEND_CONTAINER" || true
                    docker rm -f "$FRONTEND_CONTAINER" || true

                    docker run -d \\
                        --name "$BACKEND_CONTAINER" \\
                        --network "$NETWORK_NAME" \\
                        -p 8081:8081 \\
                        -e SPRING_PROFILES_ACTIVE=prod-lite \\
                        -e MONGO_URI="mongodb://$MONGO_CONTAINER:27017/imageurl" \\
                        "$BACKEND_IMAGE:$IMAGE_TAG"

                    docker run -d \\
                        --name "$FRONTEND_CONTAINER" \\
                        --network "$NETWORK_NAME" \\
                        -p 80:80 \\
                        -p 443:443 \\
                        "$FRONTEND_IMAGE:$IMAGE_TAG"
                '''
            }
        }

        stage('Deploy Monitoring') {
            steps {
                sh '''
                    docker ps -a --format '{{.Names}}' | grep -qx "$PROMETHEUS_CONTAINER" || \\
                    docker run -d \\
                        --name "$PROMETHEUS_CONTAINER" \\
                        --network "$NETWORK_NAME" \\
                        -p 9090:9090 \\
                        -v "$HOME/image-exhibition/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \\
                        prom/prometheus:latest
                    docker start "$PROMETHEUS_CONTAINER" >/dev/null 2>&1 || true

                    docker ps -a --format '{{.Names}}' | grep -qx "$GRAFANA_CONTAINER" || \\
                    docker run -d \\
                        --name "$GRAFANA_CONTAINER" \\
                        --network "$NETWORK_NAME" \\
                        -p 3000:3000 \\
                        -v grafana_data:/var/lib/grafana \\
                        grafana/grafana:latest
                    docker start "$GRAFANA_CONTAINER" >/dev/null 2>&1 || true
                '''
            }
        }

        stage('Smoke Check') {
            steps {
                sh """
                    sleep 10
                    curl -fsS http://localhost:8081/actuator/health
                    curl -fsS http://localhost:9090/-/ready
                """
            }
        }
    }

    post {
        success {
            echo "部署成功: https://${SERVER_HOST}"
            echo "后端健康检查: http://${SERVER_HOST}:8081/actuator/health"
            echo "Prometheus: http://${SERVER_HOST}:9090"
            echo "Grafana: http://${SERVER_HOST}:3000"
        }
        failure {
            echo "部署失败，请查看 Jenkins 控制台日志。"
        }
    }
}
