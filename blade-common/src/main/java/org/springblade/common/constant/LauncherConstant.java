package org.springblade.common.constant;

import org.springblade.core.launch.constant.AppConstant;

/**
 * 通用常量
 *
 * @author Chill
 */
public interface LauncherConstant {

	/**
	 * nacos 用户名
	 */
	String NACOS_USERNAME = "nacos";

	/**
	 * nacos 密码
	 */
	String NACOS_PASSWORD = "nacos";

	/**
	 * nacos 默认 namespace id
	 */
	String NACOS_NAMESPACE = "dev";

	/**
	 * nacos dev 地址
	 */
	String NACOS_DEV_ADDR = "127.0.0.1:8848";

	/**
	 * nacos prod 地址
	 */
	String NACOS_PROD_ADDR = "nacos:8848";

	/**
	 * nacos test 地址
	 */
	String NACOS_TEST_ADDR = "nacos:8848";

	/**
	 * sentinel dev 地址
	 */
	String SENTINEL_DEV_ADDR = "127.0.0.1:8858";

	/**
	 * sentinel prod 地址
	 */
	String SENTINEL_PROD_ADDR = "sentinel:8858";

	/**
	 * sentinel test 地址
	 */
	String SENTINEL_TEST_ADDR = "sentinel:8858";

	/**
	 * 动态获取nacos地址
	 *
	 * @param profile 环境变量
	 * @return addr
	 */
	static String nacosAddr(String profile) {
		String defaultAddr;
		switch (profile) {
			case (AppConstant.PROD_CODE):
				defaultAddr = NACOS_PROD_ADDR;
				break;
			case (AppConstant.TEST_CODE):
				defaultAddr = NACOS_TEST_ADDR;
				break;
			default:
				defaultAddr = NACOS_DEV_ADDR;
		}
		return configurableValue("blade.nacos.addr", "BLADE_NACOS_ADDR", defaultAddr);
	}

	/**
	 * 动态获取 nacos namespace
	 *
	 * @param profile 环境变量
	 * @return namespace
	 */
	static String nacosNamespace(String profile) {
		String defaultNamespace = isBlank(profile) ? NACOS_NAMESPACE : profile;
		return configurableValue("blade.nacos.namespace", "BLADE_NACOS_NAMESPACE", defaultNamespace);
	}

	/**
	 * 动态获取 nacos 用户名
	 *
	 * @return 用户名
	 */
	static String nacosUsername() {
		return configurableValue("blade.nacos.username", "BLADE_NACOS_USERNAME", "");
	}

	/**
	 * 动态获取 nacos 密码
	 *
	 * @return 密码
	 */
	static String nacosPassword() {
		return configurableValue("blade.nacos.password", "BLADE_NACOS_PASSWORD", "");
	}

	/**
	 * 动态获取sentinel地址
	 *
	 * @param profile 环境变量
	 * @return addr
	 */
	static String sentinelAddr(String profile) {
		String defaultAddr;
		switch (profile) {
			case (AppConstant.PROD_CODE):
				defaultAddr = SENTINEL_PROD_ADDR;
				break;
			case (AppConstant.TEST_CODE):
				defaultAddr = SENTINEL_TEST_ADDR;
				break;
			default:
				defaultAddr = SENTINEL_DEV_ADDR;
		}
		return configurableValue("blade.sentinel.addr", "BLADE_SENTINEL_ADDR", defaultAddr);
	}

	/**
	 * 优先读取 JVM 参数，其次读取环境变量，最后使用兼容默认值。
	 */
	private static String configurableValue(String propertyName, String envName, String defaultValue) {
		String propertyValue = System.getProperty(propertyName);
		if (!isBlank(propertyValue)) {
			return propertyValue;
		}
		String envValue = System.getenv(envName);
		return isBlank(envValue) ? defaultValue : envValue;
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

}
