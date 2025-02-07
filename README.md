# 压测程序使用指南

本文档提供了一个基于 Java 17 的简单 HTTP 压测工具的使用指南。通过此工具，您可以模拟并发请求以测试目标服务的性能。

---

## 1. 环境准备

### 1.1 安装 Java 17
确保您的系统已安装 OpenJDK 17。以下是一些常见操作系统的安装方法：

#### Linux (Debian/Ubuntu):
```bash
sudo apt update
sudo apt install openjdk-17-jdk
#### macOS
```bash
brew install openjdk@17
Windows:下载并安装 OpenJDK 171.2 验证安装运行以下命令，确保 Java 17 已正确安装：bashjava -version
如果安装成功，您将看到类似以下输出：
```bash
openjdk 17.0.1 2021-10-19
OpenJDK Runtime Environment Temurin-17.0.1+12 (build 17.0.1+12)
OpenJDK 64-Bit Server VM Temurin-17.0.1+12 (build 17.0.1+12, mixed mode)

安装完jdk17后
输入
```bash
javac StressTest.java
java StressTest
