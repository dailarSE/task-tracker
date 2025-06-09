// js/ui.js

/**
 * Модуль для управления UI-компонентами.
 */
window.ui = {
    // Кэшируем элементы UI
    $registerModal: $('#registerModal'),
    $loginModal: $('#loginModal'),
    $authContainer: $('#authContainer'),
    $userInfo: $('#userInfo'),
    $userEmailDisplay: $('#userEmailDisplay'),

    /**
     * Обновляет UI, чтобы отразить состояние "пользователь аутентифицирован".
     * @param {string} email - Email пользователя для отображения.
     */
    showLoggedInState: function(email) {
        this.$userEmailDisplay.text(email);
        this.$authContainer.hide();
        this.$userInfo.css('display', 'flex');
    },

    /**
     * Обновляет UI, чтобы отразить состояние "пользователь не аутентифицирован".
     */
    showLoggedOutState: function() {
        this.$userEmailDisplay.text('');
        this.$userInfo.hide();
        this.$authContainer.show();
    },

    /**
     * Обрабатывает ошибки от API и отображает их в указанном div.
     * @param {jqXHR} jqXHR - Объект jQuery XHR из колбэка error.
     * @param {jQuery} $errorElement - jQuery-объект div'а для вывода ошибки.
     */
    handleApiError: function(jqXHR, $errorElement) {
        console.error("API call failed:", jqXHR);
        let errorMessage = 'Произошла неизвестная ошибка. Пожалуйста, попробуйте позже.';

        if (jqXHR.responseJSON && jqXHR.responseJSON.detail) {
            errorMessage = jqXHR.responseJSON.detail;
        } else if (jqXHR.responseJSON && jqXHR.responseJSON.title) {
            errorMessage = jqXHR.responseJSON.title;
        }

        $errorElement.text(errorMessage);
    },

    /**
     * Проверяет начальное состояние аутентификации при загрузке страницы.
     */
    checkInitialAuthState: function() {
        const token = localStorage.getItem('jwt_token');
        if (token) {
            window.taskTrackerApi.getCurrentUser(token)
                .done((user) => {
                    this.showLoggedInState(user.email);
                })
                .fail(() => {
                    console.error("Token from localStorage is invalid or expired. Clearing token.");
                    localStorage.removeItem('jwt_token');
                    this.showLoggedOutState();
                });
        } else {
            this.showLoggedOutState();
        }
    }
};