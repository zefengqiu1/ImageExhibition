# 使用官方 Nginx 镜像作为基础镜像
FROM nginx:latest

# 复制本地 nginx 配置文件到容器中
COPY ./nginx.conf /etc/nginx/nginx.conf
COPY ./lsp66.com.cert /etc/nginx/lsp66.com.cert
COPY ./lsp66.com.key /etc/nginx/lsp66.com.key
COPY default.conf /etc/nginx/conf.d/default.conf
COPY ./build /etc/nginx/build

# 删除默认配置文件，避免冲突
#RUN rm /etc/nginx/conf.d/default.conf

# 暴露 80 和 443 端口

# 启动 Nginx 服务
CMD ["nginx", "-g", "daemon off;"]