package uw.mydb.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uw.mydb.conf.MydbConfig;
import uw.mydb.conf.MydbConfigManager;

import java.util.*;

/**
 * 路由管理器。
 *
 * @author axeon
 */
public class RouteManager {

    private static final Logger logger = LoggerFactory.getLogger(RouteManager.class);

    /**
     * 算法实例的管理器
     */
    private static Map<String, List<RouteAlgorithm>> routeAlgorithmMap = new HashMap<>();

    /**
     * 配置信息。
     */
    private static MydbConfig config = MydbConfigManager.getConfig();


    /**
     * 初始化管理器，缓存算法实例。
     */
    public static void init() {
        //填充算法列表。
        for (MydbConfig.RouteConfig routeConfig : config.getRoutes().values()) {
            List<MydbConfig.DataNodeConfig> dataNodeConfigs = routeConfig.getDataNodes();
            List<MydbConfig.AlgorithmConfig> algorithmConfigs = routeConfig.getAlgorithms();
            ArrayList<RouteAlgorithm> routeAlgorithms = new ArrayList<>();
            if (routeConfig.getParent() != null) {
                if (!routeAlgorithmMap.containsKey(routeConfig.getParent())) {
                    logger.error("RouteConfig[{}]未找到指定的父级配置[{}]", routeConfig.getName(), routeConfig.getParent());
                }
                routeAlgorithms.addAll(routeAlgorithmMap.get(routeConfig.getParent()));
            }
            for (MydbConfig.AlgorithmConfig algorithmConfig : algorithmConfigs) {
                try {
                    Class clazz = Class.forName(algorithmConfig.getAlgorithm());
                    Object object = clazz.newInstance();
                    if (object instanceof RouteAlgorithm) {
                        RouteAlgorithm algorithm = (RouteAlgorithm) object;
                        algorithm.init(routeConfig.getName(), algorithmConfig, dataNodeConfigs);
                        algorithm.config();
                        routeAlgorithms.add(algorithm);
                    }
                } catch (Exception e) {
                    logger.error("算法类加载失败！" + e.getMessage(), e);
                }
            }
            routeAlgorithmMap.put(routeConfig.getName(), routeAlgorithms);
        }
    }

    /**
     * 根据route名称获得算法列表。
     *
     * @return
     */
    public static List<RouteAlgorithm> getRouteAlgorithmList(String route) {
        return routeAlgorithmMap.get(route);
    }

    /**
     * 获得tableConfig配置。
     *
     * @param tablename
     * @return
     */
    public static MydbConfig.TableConfig getTableConfig(MydbConfig.SchemaConfig schema, String tablename) {
        return schema.getTables().get(tablename);
    }

    /**
     * 获得匹配列的map。
     *
     * @param tableConfig
     * @return
     */
    public static RouteAlgorithm.RouteKeyData getParamMap(RouteAlgorithm.RouteKeyData keyData, MydbConfig.TableConfig tableConfig) {
        if (tableConfig == null) {
            return null;
        }
        MydbConfig.RouteConfig routeConfig = config.getRoutes().get(tableConfig.getRoute());
        if (routeConfig == null) {
            return null;
        }
        //加载父级路由信息。
        if (routeConfig.getParent() != null) {
            MydbConfig.RouteConfig parentRoute = config.getRoutes().get(routeConfig.getParent());
            if (parentRoute != null) {
                List<MydbConfig.AlgorithmConfig> algorithmConfigs = parentRoute.getAlgorithms();
                for (MydbConfig.AlgorithmConfig algorithmConfig : algorithmConfigs) {
                    if (keyData.getValue(algorithmConfig.getRouteKey()) == null) {
                        keyData.initKey(algorithmConfig.getRouteKey());
                    }
                }
            }
        }
        //加载本级路由信息。
        List<MydbConfig.AlgorithmConfig> algorithmConfigs = routeConfig.getAlgorithms();
        for (MydbConfig.AlgorithmConfig algorithmConfig : algorithmConfigs) {
            if (keyData.getValue(algorithmConfig.getRouteKey()) == null) {
                keyData.initKey(algorithmConfig.getRouteKey());
            }
        }
        return keyData;
    }

    /**
     * 获得路由信息。
     *
     * @param tableConfig
     * @param keyData
     * @return
     */
    public static RouteAlgorithm.RouteInfoData calculate(MydbConfig.TableConfig tableConfig, RouteAlgorithm.RouteKeyData keyData) throws RouteAlgorithm.RouteException {
        RouteAlgorithm.RouteInfoData routeInfoData = new RouteAlgorithm.RouteInfoData();
        //构造空路由配置。
        RouteAlgorithm.RouteInfo routeInfo = RouteAlgorithm.RouteInfo.newDataWithTable(tableConfig.getName());
        routeInfoData.setSingle(routeInfo);
        //获得路由算法列表。
        List<RouteAlgorithm> routeAlgorithms = getRouteAlgorithmList(tableConfig.getRoute());
        if (routeAlgorithms == null) {
            return routeInfoData;
        }
        for (RouteAlgorithm routeAlgorithm : routeAlgorithms) {
            RouteAlgorithm.RouteKeyValue value = keyData.getValue(routeAlgorithm.getAlgorithmConfig().getRouteKey());
            //优化一下caclType。
            value.calcType();
            if (value.getType() == RouteAlgorithm.RouteKeyValue.SINGLE) {
                routeInfo = routeAlgorithm.calculate(tableConfig, routeInfo, value.getValue1());
                routeInfoData.setSingle(routeInfo);
            } else if (value.getType() == RouteAlgorithm.RouteKeyValue.RANGE) {
                Set<RouteAlgorithm.RouteInfo> set = new HashSet<>();
                if (routeInfoData.isSingle()) {
                    List<RouteAlgorithm.RouteInfo> list = routeAlgorithm.calculateRange(tableConfig, RouteAlgorithm.RouteInfo.newListWithRouteInfo(routeInfoData.getRouteInfo()), value.getValue1(), value.getValue2());
                    set.addAll(list);
                } else {
                    for (RouteAlgorithm.RouteInfo ri : routeInfoData.getRouteInfos()) {
                        List<RouteAlgorithm.RouteInfo> list = routeAlgorithm.calculateRange(tableConfig, RouteAlgorithm.RouteInfo.newListWithRouteInfo(ri), value.getValue1(), value.getValue2());
                        set.addAll(list);
                    }
                }
                routeInfoData.setAll(set);
            } else if (value.getType() == RouteAlgorithm.RouteKeyValue.MULTI) {
                Set<RouteAlgorithm.RouteInfo> set = new HashSet<>();
                if (routeInfoData.isSingle()) {
                    Map<String, RouteAlgorithm.RouteInfo> map = routeAlgorithm.calculate(tableConfig, RouteAlgorithm.RouteInfo.newMapWithRouteInfo(routeInfoData.getRouteInfo()), value.getValues());
                    set.addAll(map.values());
                } else {
                    for (RouteAlgorithm.RouteInfo ri : routeInfoData.getRouteInfos()) {
                        Map<String, RouteAlgorithm.RouteInfo> map = routeAlgorithm.calculate(tableConfig, RouteAlgorithm.RouteInfo.newMapWithRouteInfo(ri), value.getValues());
                        set.addAll(map.values());
                    }
                }
                routeInfoData.setAll(set);
            } else {
                //此时说明参数没有匹配上。
                routeInfo = routeAlgorithm.getDefaultRoute(tableConfig, routeInfo);
                routeInfoData.setSingle(routeInfo);
            }
        }
        return routeInfoData;
    }

    /**
     * 获得所有表的信息。
     *
     * @param tableConfig
     * @return
     */
    public static List<RouteAlgorithm.RouteInfo> getAllRouteList(MydbConfig.TableConfig tableConfig) throws RouteAlgorithm.RouteException {
        List<RouteAlgorithm> routeAlgorithms = getRouteAlgorithmList(tableConfig.getRoute());
        List<RouteAlgorithm.RouteInfo> routeInfo = new ArrayList<>();
        for (RouteAlgorithm routeAlgorithm : routeAlgorithms) {
            routeInfo = routeAlgorithm.getAllRouteList(tableConfig, routeInfo);
        }
        return routeInfo;
    }

    /**
     * 获得要创建表的信息。
     *
     * @param tableConfig
     * @return
     */
    public static List<RouteAlgorithm.RouteInfo> getRouteListForCreate(MydbConfig.TableConfig tableConfig) throws RouteAlgorithm.RouteException {
        List<RouteAlgorithm> routeAlgorithms = getRouteAlgorithmList(tableConfig.getRoute());
        List<RouteAlgorithm.RouteInfo> routeInfo = new ArrayList<>();
        for (RouteAlgorithm routeAlgorithm : routeAlgorithms) {
            routeInfo = routeAlgorithm.getRouteListForCreate(tableConfig, routeInfo);
        }
        return routeInfo;
    }
}
