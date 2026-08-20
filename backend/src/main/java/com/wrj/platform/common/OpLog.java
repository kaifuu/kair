package com.wrj.platform.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 操作日志注解:打在写操作接口上,由 OpLogAspect 自动记录 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 模块名,如「设备管理」 */
    String module();

    /** 动作,如「新增」「删除」 */
    String action();
}
