package uw.mydb.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * influxDB 操作类
 *
 * @author axeon
 */
@Component
public class InfluxDBService {

    private static final Logger log = LoggerFactory.getLogger(InfluxDBService.class);

//    @Bean
//    InfluxDB influxDB() {
//        MydbConfig.MetricService config = MydbConfigManager.getConfig().getStats().getMetricService();
//        InfluxDB influxDB = InfluxDBFactory.connect(config.getHost(), config.getUsername(), config.getPassword());
//        influxDB.enableBatch(100, 30, TimeUnit.SECONDS);
//        return influxDB;
//    }

    /**
     * 更新mydb的sql统计数据。
     */
//    @Scheduled(fixedRate=${"uw.mydb.stats.metric-service.interval"})
    public void updateMydbSqlStats() {

    }


}
