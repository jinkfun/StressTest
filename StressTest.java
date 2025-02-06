import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.net.*;
import javax.net.ssl.*;
import java.security.*;
import java.security.cert.X509Certificate;

public class StressTest {

    // ================= 配置参数 =================
    private static final String TARGET_URL = "http://example.com";//换成你要压测的URL
    private static final int THREAD_COUNT = 200;         // 并发线程数
    private static final int REQUEST_INTERVAL = 0;      // 请求间隔毫秒（0表示无间隔）
    private static final boolean DISABLE_SSL_VALIDATION = false; // 是否禁用SSL证书验证
    private static final boolean FOLLOW_REDIRECTS = true;       // 是否自动跟随重定向

    // Chrome浏览器请求头
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                            "Chrome/91.0.4472.124 Safari/537.36";

    // ================= 统计计数器 =================
    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong successfulRequests = new AtomicLong(0);
    private static final AtomicLong failedRequests = new AtomicLong(0);
    private static final AtomicLong totalResponseTime = new AtomicLong(0);
    private static final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

    static {
        CookieHandler.setDefault(new CookieManager());
        if (DISABLE_SSL_VALIDATION) {
            disableSSLCertificateValidation();
        }
    }

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        
        // 增强关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // 1. 停止接收新请求
                executor.shutdown();
                
                // 2. 等待现有请求完成
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            // 3. 显示最终统计
            printFinalStatistics();
        }));

        // 启动工作线程
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.execute(() -> {
                while (true) {
                    long requestStart = System.currentTimeMillis();
                    HttpURLConnection connection = null;
                    try {
                        URL url = new URL(TARGET_URL);
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setInstanceFollowRedirects(FOLLOW_REDIRECTS);
                        connection.setRequestMethod("GET");
                        connection.setConnectTimeout(5000);
                        connection.setReadTimeout(5000);
                        
                        // 设置请求头
                        connection.setRequestProperty("User-Agent", USER_AGENT);
                        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

                        if (connection instanceof HttpsURLConnection && DISABLE_SSL_VALIDATION) {
                            ((HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true);
                        }

                        int responseCode = connection.getResponseCode();
                        if (responseCode >= 200 && responseCode < 300) {
                            successfulRequests.incrementAndGet();
                        } else {
                            failedRequests.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failedRequests.incrementAndGet();
                    } finally {
                        if (connection != null) connection.disconnect();
                        totalResponseTime.addAndGet(System.currentTimeMillis() - requestStart);
                        totalRequests.incrementAndGet();
                    }

                    try {
                        if (REQUEST_INTERVAL > 0) Thread.sleep(REQUEST_INTERVAL);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }

        // 统计线程（守护线程）
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000);
                    printInterimStatistics();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    private static void printStatistics(String title) {
        long elapsed = (System.currentTimeMillis() - startTime.get()) / 1000;
        long total = totalRequests.get();
        long success = successfulRequests.get();
        long failed = failedRequests.get();
        long avgTime = total == 0 ? 0 : totalResponseTime.get() / total;
        long qps = elapsed == 0 ? 0 : total / elapsed;

        String status = title.equals("最终") ? "[运行结束] " : "[运行中] ";
        System.out.printf("\n%s========== 压力测试统计（%s）==========\n", status, title);
        System.out.println("目标地址: " + TARGET_URL);
        System.out.println("活跃线程: " + THREAD_COUNT);
        System.out.println("运行时间: " + elapsed + "s");
        System.out.println("总请求数: " + total);
        System.out.printf("成功请求: %d (%.1f%%)\n", success, total == 0 ? 0 : success * 100.0 / total);
        System.out.printf("失败请求: %d (%.1f%%)\n", failed, total == 0 ? 0 : failed * 100.0 / total);
        System.out.println("平均响应时间: " + avgTime + "ms");
        System.out.println("QPS: " + qps);
        System.out.println("=================================");
    }

    private static void printInterimStatistics() {
        printStatistics("实时");
    }

    private static void printFinalStatistics() {
        printStatistics("最终");
    }

    private static void disableSSLCertificateValidation() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }
}