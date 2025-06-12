// js/api.js

/**
 * Модуль для взаимодействия с Backend API.
 * Все функции возвращают jQuery Promise (jqXHR), что позволяет
 * использовать .done() и .fail() для обработки ответов.
 */
window.taskTrackerApi = {
    BASE_URL: 'http://localhost:8080/api/v1',

    /**
     * Отправляет запрос на регистрацию пользователя.
     * @param {object} registrationData - Данные для регистрации { email, password, repeatPassword }.
     * @returns {Promise} jQuery Promise.
     */
    registerUser: function (registrationData) {
        return $.ajax({
            url: `${this.BASE_URL}/users/register`,
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(registrationData)
        });
    },

    /**
     * Отправляет запрос на аутентификацию пользователя.
     * @param {object} loginData - Данные для входа { email, password }.
     * @returns {Promise} jQuery Promise.
     */
    loginUser: function (loginData) {
        return $.ajax({
            url: `${this.BASE_URL}/auth/login`,
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(loginData)
        });
    },
    /**
     * Запрашивает данные текущего аутентифицированного пользователя.
     * Требует наличия валидного JWT в заголовках.
     * @param {string} token - JWT токен.
     * @returns {Promise} jQuery Promise.
     */
    getCurrentUser: function (token) {
        return $.ajax({
            url: `${this.BASE_URL}/users/me`,
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        });
    },

    /**
     * Запрашивает список всех задач для текущего пользователя.
     * @param {string} token - JWT токен.
     * @returns {Promise} jQuery Promise, который в случае успеха вернет массив объектов задач.
     */
    getTasks: function(token) {
        return $.ajax({
            url: `${this.BASE_URL}/tasks`,
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        });
    },

    /**
     * Создает новую задачу.
     * @param {object} taskData - Данные для создания задачи. Ожидается объект вида {title: "..."}.
     * @param {string} token - JWT токен.
     * @returns {Promise} jQuery Promise, который в случае успеха вернет созданный объект задачи.
     */
    createTask: function(taskData, token) {
        return $.ajax({
            url: `${this.BASE_URL}/tasks`,
            method: 'POST',
            contentType: 'application/json',
            headers: {
                'Authorization': 'Bearer ' + token
            },
            data: JSON.stringify(taskData)
        });
    }
};