# NIplayer

## 简介

NIplayer 是一个本地视频播放器，基于弹弹play二次开发，致力于提供纯净的视频播放体验。

## 下载

安卓平台:

[Release 页面](https://github.com/nichx/NIplayer/releases)

## 一、 应用介绍

## 功能介绍

- 视频
  - 提供双内核（IJK、EXO）切换，适配常见视频格式
  - 支持局域网文件浏览播放
  - 支持FTP文件浏览播放
  - 支持WebDav文件浏览播放
- 字幕
  - 支持根据视频自动匹配字幕
  - 支持字幕搜索、下载
  - 支持字幕样式调整，大小、描边、颜色等
  - 支持外挂字幕

## 项目介绍

使用Kotlin + MVVM + 组件化方案实现

## 项目结构

## 模块介绍

| 模块     | 说明                             |
| ------ | ------------------------------ |
| APP    | 项目入口，包含启动页及主框架                 |
| Local  | 本地数据模块，包含本地视频、字幕下载             |
| User   | 用户模块，包含用户信息、应用设置等              |
| Player | 播放器模块                          |
| Common | 基础模块，包括基类、通用组件、工具类等            |
| Data   | 数据模块，包含普通Bean类、数据库Entity类、枚举类等 |

## 项目配置

1.日志开关，根目录下gradle.properties文件，配置IS\_DEBUG\_MODE，修改后rebuild project

2.单独编译模块，根目录下gradle.properties文件，配置IS\_APPLICATION\_RUN，设置true代表模块以应用类型编译，修改后rebuild project

## 修改说明

此版本基于弹弹play进行了以下修改：

- 移除了 bilibili 弹幕相关功能
- 移除了投屏功能

