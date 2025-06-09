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
    registerUser: function(registrationData) {
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
    loginUser: function(loginData) {
        return $.ajax({
            url: `${this.BASE_URL}/auth/login`,
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(loginData)
        });
    }
};