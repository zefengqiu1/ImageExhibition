import * as Axios from 'axios';

export const axios = Axios.default.create({
  // baseURL: 'https://lsp66.com',
  // baseURL: 'http://localhost:8081',
  baseURL: process.env.REACT_APP_API_BASE_URL || '',
  // headers: {
  //   'Authorization': 'Bearer ' + localStorage.getItem('token'),  // 添加 Authorization 头
  //   // 自定义头
  // }
});

// 请求拦截器，添加 Authorization 头
// axios.interceptors.request.use(config => {
//   const token = localStorage.getItem('token');  // 从 localStorage 获取 token
//   if (token) {
//     config.headers['Authorization'] = `Bearer ${token}`;  // 设置 token
//     config.headers['Access-Control-Allow-Origin'] = '*';
//     config.headers['Access-Control-Allow-Methods'] = 'GET,PUT,POST,DELETE,PATCH,OPTIONS';
//   }
//   return config;
// }, error => {
//   return Promise.reject(error);
// });

// 响应拦截器，处理 401 错误
// axios.interceptors.response.use(response => {
//   return response;
// }, error => {
//   if (error.response && error.response.status === 401) {
//     // 如果返回 401 错误，跳转到登录页面
//     window.location.href = 'http://localhost:3000/login';  // 跳转到登录页面
//   }
//   return Promise.reject(error);
// });
