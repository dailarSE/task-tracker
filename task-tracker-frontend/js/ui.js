// js/ui.js

/**
 * Модуль для управления UI-компонентами.
 */
window.ui = {
    // --- Кэшированные элементы UI ---
    $registerModal: $('#registerModal'),
    $loginModal: $('#loginModal'),
    $authContainer: $('#authContainer'),
    $userInfo: $('#userInfo'),
    $userEmailDisplay: $('#userEmailDisplay'),
    $toast: $('#toastNotification'),
    $toastMessage: $('#toastMessage'),
    toastTimer: null, // Таймер для скрытия toast'а

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

    /** Очищает все сообщения и подсветку ошибок внутри формы. */
    clearFormErrors: function($form) {
        $form.find('.is-invalid').removeClass('is-invalid');
        $form.find('.field-error-message').text('');
        $form.find('.general-error-message').text('');
    },

    /** Показывает общую ошибку для формы. */
    showGeneralError: function($form, message) {
        $form.find('.general-error-message').html(message); // .html() чтобы можно было вставить ссылку
    },

    /** Подсвечивает поля с ошибками валидации. */
    applyValidationErrors: function($form, invalidParams) {
        if (!invalidParams || !Array.isArray(invalidParams)) return;

        invalidParams.forEach(error => {
            const $field = $form.find(`[data-field-name="${error.field}"]`);
            if ($field.length) {
                $field.addClass('is-invalid');
                $field.siblings('.field-error-message').text(error.message);
            }
        });
    },

    /**
     * Показывает всплывающее toast-уведомление.
     * @param {string} message - Сообщение для отображения.
     * @param {string} type - Тип уведомления ('success', 'error', 'warning'), определяет цвет.
     */
    showToastNotification: function(message, type = 'error') {
        // Очищаем предыдущий таймер, если он был
        if (this.toastTimer) {
            clearTimeout(this.toastTimer);
        }

        this.$toastMessage.text(message);
        // Устанавливаем класс для цвета
        this.$toast.removeClass('success error warning').addClass(type);
        // Показываем toast
        this.$toast.addClass('show');

        // Устанавливаем таймер на скрытие через 5 секунд
        this.toastTimer = setTimeout(() => {
            this.$toast.removeClass('show');
        }, 5000);
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
                .fail((jqXHR) => {
                    console.error("Token from localStorage is invalid or expired. Clearing token.");
                    localStorage.removeItem('jwt_token');
                    this.showLoggedOutState();

                    const problem = jqXHR.responseJSON;
                    let toastMessage = 'An error occurred with your session. Please log in again.';
                    let toastType = 'error';

                    if (problem && problem.detail) {
                        toastMessage = problem.detail;
                    } else if (problem && problem.title) {
                        toastMessage = problem.title;
                    }

                    if (problem && problem.type && problem.type.includes('/jwt/expired')) {
                        toastMessage = 'Your session has expired. Please log in again.';
                        toastType = 'warning';
                    }

                    this.showToastNotification(toastMessage, toastType);
                });
        } else {
            this.showLoggedOutState();
        }
    }
};