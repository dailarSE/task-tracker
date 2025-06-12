// js/api.js

/**
 * Модуль для взаимодействия с Backend API.
 * Все функции возвращают jQuery Promise (jqXHR), что позволяет
 * использовать .done() и .fail() для обработки ответов.
 */
window.taskTrackerApi = {
    BASE_URL: 'http://localhost:8080/api/v1',

    /**
     * Централизованный обработчик 401 ошибок. Вызывается автоматически при любом 401 ответе.
     * @param {object} jqXHR - Объект jqXHR из fail-колбэка jQuery.
     */
    _handle401Error: function(jqXHR) {
        // Если токена уже нет (другой запрос уже разлогинил), ничего не делаем, чтобы избежать "гонки".
        if (!localStorage.getItem('jwt_token')) return;
        console.error("Intercepted 401 Unauthorized. Logging out.", jqXHR.responseJSON);

        const problem = jqXHR.responseJSON;
        const defaultMessage = 'Your session is invalid. Please log in again.';
        let toastMessage = problem?.detail || problem?.title || defaultMessage;
        let toastType = 'error';

        if (problem?.type?.includes('/jwt/expired')) {
            toastMessage = 'Your session has expired. Please log in again.';
            toastType = 'warning';
        }

        window.auth.handleLogout();
        window.ui.showToastNotification(toastMessage, toastType);
    },

    /**
     * Приватная функция-обертка для всех защищенных запросов.
     * @param {object} ajaxOptions - Стандартные опции для $.ajax.
     * @returns {Promise} jQuery Promise.
     */
    _request: function(ajaxOptions) {
        const token = localStorage.getItem('jwt_token');
        if (token) {
            ajaxOptions.headers = {
                ...ajaxOptions.headers, 'Authorization': 'Bearer ' + token
            };
        }

        const deferred = $.Deferred();

        // Делаем оригинальный AJAX-запрос
        $.ajax(ajaxOptions)
            .done((data, textStatus, jqXHR) => {
                // Если все хорошо, "пробрасываем" успех в наш новый промис
                deferred.resolve(data, textStatus, jqXHR);
            })
            .fail((jqXHR) => {
                if (jqXHR.status === 401) {
                    // Обрабатываем 401 здесь и ТОЛЬКО здесь.
                    this._handle401Error(jqXHR);
                } else {
                    // Для всех остальных ошибок "пробрасываем" их в наш новый промис.
                    deferred.reject(jqXHR);
                }
            });

        // Возвращаем наш кастомный промис, а не оригинальный от $.ajax
        return deferred.promise();
    },

    // --- Публичные методы API ---

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

    // --- Защищенные методы ---
    // используют _request
    /**
     * Запрашивает данные текущего аутентифицированного пользователя.
     * @returns {Promise} jQuery Promise.
     */
    getCurrentUser: function () {
        return this._request({
            url: `${this.BASE_URL}/users/me`,
            method: 'GET'
        });
    },

    /**
     * Запрашивает список всех задач для текущего пользователя.
     * @returns {Promise} jQuery Promise, который в случае успеха вернет массив объектов задач.
     */
    getTasks: function() {
        return this._request({
            url: `${this.BASE_URL}/tasks`,
            method: 'GET'
        });
    },

    /**
     * Создает новую задачу.
     * @param {object} taskData - Данные для создания задачи. Ожидается объект вида {title: "..."}.
     * @returns {Promise} jQuery Promise, который в случае успеха вернет созданный объект задачи.
     */
    createTask: function(taskData) {
        return this._request({
            url: `${this.BASE_URL}/tasks`,
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(taskData)
        });
    }
};