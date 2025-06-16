/**
 * @file ui.js
 * @description Модуль для управления ОБЩИМИ, переиспользуемыми UI-компонентами
 * и их обработчиками. Отвечает за модальные окна регистрации/логина,
 * toast-уведомления и глобальное состояние UI (залогинен/не залогинен).
 */

/**
 * Глобальный объект `ui`, инкапсулирующий все общие UI-хелперы и их инициализацию.
 * @namespace ui
 */
window.ui = {
    // --- Кэшированные jQuery-объекты для общих элементов ---
    $registerModal: $('#registerModal'),
    $loginModal: $('#loginModal'),
    $authContainer: $('#authContainer'),
    $userInfo: $('#userInfo'),
    $userEmailDisplay: $('#userEmailDisplay'),
    $toast: $('#toastNotification'),
    $toastMessage: $('#toastMessage'),
    toastTimer: null, // Таймер для автоматического скрытия toast-уведомления

    /**
     * Инициализирует все общие обработчики событий UI.
     * Эта функция должна вызываться один раз при старте приложения из `main.js`.
     */
    init: function() {
        this._initModalHandlers();
        this._initGlobalEventHandlers();
        console.log("Common UI handlers initialized.");
    },

    /**
     * Приватный метод для инициализации обработчиков общих модальных окон (регистрация/логин).
     * @private
     */
    _initModalHandlers: function() {
        const self = this;

        // --- Обработчики ОТКРЫТИЯ ---
        $('#showRegisterModalBtn').on('click', () => {
            self.clearFormErrors(self.$registerModal);
            self.$registerModal.css('display', 'flex');
        });

        $('#showLoginModalBtn').on('click', () => {
            self.clearFormErrors(self.$loginModal);
            self.$loginModal.css('display', 'flex');
        });

        // --- Обработчики ЗАКРЫТИЯ ---

        // Закрытие по кнопке "крестик" для ВСЕХ модальных окон.
        // Окно редактирования задач будет иметь свой собственный обработчик в `tasks.js`.
        $('.modal .close-modal-btn').on('click', function() {
            // Убеждаемся, что мы не закрываем окно редактирования задач,
            // так как у него своя, более сложная логика закрытия.
            const $modal = $(this).closest('.modal');
            if (!$modal.is('#taskEditModal')) {
                $modal.css('display', 'none');
            }
        });

        // Закрытие по клику на фон (для всех модалок, кроме окна редактирования)
        let mousedownOnBackdropFor = null;
        $('.modal').on('mousedown', function(event) {
            if (event.target === this) {
                mousedownOnBackdropFor = $(this);
            } else {
                mousedownOnBackdropFor = null;
            }
        });

        $('.modal').on('mouseup', function(event) {
            if (mousedownOnBackdropFor && mousedownOnBackdropFor.is($(this)) && event.target === this) {
                if (!$(this).is('#taskEditModal')) {
                    $(this).css('display', 'none');
                }
            }
            mousedownOnBackdropFor = null;
        });
    },

    /**
     * Приватный метод для инициализации глобальных подписчиков на события.
     * @private
     */
    _initGlobalEventHandlers: function() {
        // Подписываемся на глобальное событие ошибки, чтобы показывать toast.
        window.eventBus.on('app:error', (event, errorData) => {
            if (errorData && errorData.message) {
                this.showToastNotification(errorData.message, 'error');
            }
        });
    },

    // --- ПУБЛИЧНЫЕ МЕТОДЫ-ХЕЛПЕРЫ ---

    /**
     * Обновляет UI, чтобы отразить состояние "пользователь аутентифицирован".
     * Скрывает кнопки входа/регистрации и показывает блок с email'ом пользователя.
     * @param {string} email - Email пользователя для отображения.
     */
    showLoggedInState: function(email) {
        this.$userEmailDisplay.text(email);
        this.$authContainer.hide();
        this.$userInfo.css('display', 'flex');
        window.tasksUi.show(); // Показываем контейнер с задачами
    },

    /**
     * Обновляет UI, чтобы отразить состояние "пользователь не аутентифицирован".
     */
    showLoggedOutState: function() {
        this.$userEmailDisplay.text('');
        this.$userInfo.hide();
        this.$authContainer.show();
        window.tasksUi.hide(); // Скрываем контейнер с задачами
    },

    /**
     * Сбрасывает ВЕСЬ UI в начальное, "незалогиненное" состояние.
     * Вызывается при логауте.
     */
    resetAllUIStateToLoggedOut: function() {
        this.showLoggedOutState();
        this.clearFormErrors(this.$registerModal);
        this.$registerModal.find('form').trigger('reset');
        this.clearFormErrors(this.$loginModal);
        this.$loginModal.find('form').trigger('reset');
        window.tasksUi.clearCreateTaskForm();
    },

    /**
     * Очищает все сообщения и подсветку ошибок внутри указанной формы.
     * @param {jQuery} $formContainer - jQuery-объект контейнера формы (например, модального окна).
     */
    clearFormErrors: function($formContainer) {
        $formContainer.find('.is-invalid').removeClass('is-invalid');
        $formContainer.find('.field-error-message').text('');
        $formContainer.find('.general-error-message').empty(); // .empty() для удаления HTML-элементов, например, ссылок
    },

    /**
     * Показывает общее сообщение об ошибке для формы.
     * @param {jQuery} $formContainer - jQuery-объект контейнера формы.
     * @param {string|jQuery} message - Сообщение для отображения (может быть строкой или jQuery-объектом).
     */
    showGeneralError: function($formContainer, message) {
        $formContainer.find('.general-error-message').html(message);
    },

    /**
     * Применяет ошибки валидации к полям формы, подсвечивая их.
     * @param {jQuery} $formContainer - jQuery-объект контейнера формы.
     * @param {Array<object>} invalidParams - Массив объектов ошибок из ProblemDetail.
     */
    applyValidationErrors: function($formContainer, invalidParams) {
        if (!invalidParams || !Array.isArray(invalidParams)) return;
        invalidParams.forEach(error => {
            const fieldName = error.field;
            if (!fieldName) return;
            const $field = $formContainer.find(`[data-field-name="${fieldName}"]`);
            const $errorContainer = $formContainer.find(`.field-error-message[data-field-name="${fieldName}"]`);
            if ($field.length) $field.addClass('is-invalid');
            if ($errorContainer.length) $errorContainer.text(error.message);
        });
    },

    /**
     * Показывает всплывающее toast-уведомление.
     * @param {string} message - Сообщение для отображения.
     * @param {'success'|'info'|'error'|'warning'} [type='error'] - Тип уведомления, определяет цвет.
     */
    showToastNotification: function(message, type = 'error') {
        if (this.toastTimer) {
            clearTimeout(this.toastTimer);
        }
        this.$toastMessage.text(message);
        this.$toast.removeClass('success info error warning').addClass(type);
        this.$toast.addClass('show');
        this.toastTimer = setTimeout(() => {
            this.$toast.removeClass('show');
        }, 5000);
    },

    /**
     * Запрашивает данные текущего пользователя для инициализации UI.
     * Этот метод просто инициирует API-запрос и возвращает Promise.
     * Дальнейшая логика (загрузка задач и т.д.) обрабатывается в `auth.js`.
     * @returns {Promise} jQuery Promise от API-вызова.
     */
    checkInitialAuthState: function() {
        const token = localStorage.getItem('jwt_token');
        if (token) {
            return window.taskTrackerApi.getCurrentUser();
        } else {
            this.showLoggedOutState();
            return $.Deferred().reject().promise();
        }
    },

    /**
     * Блокирует кнопку отправки формы.
     * @param {jQuery} $formContainer - jQuery-объект контейнера формы.
     */
    lockForm: function($formContainer) {
        $formContainer.find('button[type="submit"]').prop('disabled', true);
    },

    /**
     * Разблокирует кнопку отправки формы.
     * @param {jQuery} $formContainer - jQuery-объект контейнера формы.
     */
    unlockForm: function($formContainer) {
        $formContainer.find('button[type="submit"]').prop('disabled', false);
    }
};