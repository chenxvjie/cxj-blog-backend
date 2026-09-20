# Flow JAR 制品部署

本方案是云效构建JAR并上传Packages通用制品，服务器构建仅含JRE和JAR的运行镜像；无需另购Docker镜像仓库。构建来源是本次流水线代码，不在服务器git pull。

## 首次安装

将本仓库 deploy/deploy-backend.sh 通过SFTP上传到服务器 /opt/cxj-blog/deploy/deploy-backend.sh。保持LF换行，不把包含凭证的.env复制到制品中。Runner部署用户为root，脚本应由管理员维护：

```bash
sudo chown root:root /opt/cxj-blog/deploy/deploy-backend.sh
sudo chmod 700 /opt/cxj-blog/deploy/deploy-backend.sh
sudo bash -n /opt/cxj-blog/deploy/deploy-backend.sh
sudo mkdir -p /opt/cxj-blog/incoming/backend
```

默认Compose项目名需仍为deploy；原部署需要postgres/backend/frontend/nginx四个服务正在运行。脚本从现有backend容器获取旧镜像作为回滚目标。

## 构建上传

JDK21、Maven3.9；前置单元测试必须通过。构建命令：

```bash
set -eu
mvn -B -s /root/.m2/settings.xml clean package -DskipTests
mkdir -p release
cp target/cxj-blog-backend-0.0.1-SNAPSHOT.jar release/app.jar
```

通用制品仓库选择cxj-blog-artifacts，制品名称Artifacts_${PIPELINE_ID}，版本${BUILD_NUMBER}；打包路径release/，不勾选包含打包路径。解压后根目录必须有app.jar。

## 主机部署界面

选择本次构建产出的制品和cxj-blog-prod主机组，下载路径 /opt/cxj-blog/incoming/backend/package.tgz，执行用户root，分批数量1，不暂停，超时30分钟。部署脚本：

```bash
bash /opt/cxj-blog/deploy/deploy-backend.sh /opt/cxj-blog/incoming/backend/package.tgz
```

流水线并发设为1/排队，不允许后端两次运行同时下载覆盖同一制品路径。脚本部署锁只能保护执行过程，不能保护云效提前进行的下载步骤。未来前端部署也必须使用同一 /opt/cxj-blog/deploy/.deployment.lock 锁。

## 验收与运维

脚本保留独立release目录，以JAR完整SHA256命名镜像，在更新前通过容器pg_dump备份数据库，健康检查通过后重载Nginx。失败尝试恢复旧应用镜像并再次健康检查，流水线仍返回失败。

首次运行后生成docker-compose.flow.yml覆盖后端image。后续手工操作需同时传入两个-f参数，避免意外退回旧源码构建的镜像：

```bash
cd /opt/cxj-blog/deploy
docker compose --env-file .env -f docker-compose.prod.yml -f docker-compose.flow.yml ps
```

这不是无停机部署；单后端容器重建存在短暂停机。数据库回滚不自动执行，DDL必须向后兼容，必要时人工恢复备份。备份和旧镜像会占磁盘空间，目前保留不自动删除，生产需要安排保留策略及异机备份。飞书成功通知仅放在本任务成功后；错误码/健康检查超时应通知失败。

本地只完成脚本语法校验；真实Runner下载格式、镜像构建、数据库备份、健康检查和回滚必须首次手动运行验证。脚本不是已经部署到服务器的文件。
