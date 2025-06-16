/**
 * Модуль для управления UI-компонентами.
 */
window.ui = {
    // --- Кэшированные элементы UI ---
    $registerModal: $('#registerModal'),
    $loginModal: $('#loginModal'),
    $taskEditModal: $('#taskEditModal'),
    $authContainer: $('#authContainer'),
    $userInfo: $('#userInfo'),
    $userEmailDisplay: $('#userEmailDisplay'),
    $toast: $('#toastNotification'),
    $toastMessage: $('#toastMessage'),
    toastTimer: null, // Таймер для скрытия toast'а

    /**
     * Инициализирует все общие обработчики событий UI.
     * Должна вызываться один раз при старте приложения.
     */
    init: function() {
        this._initModalHandlers();
        this._initGlobalEventHandlers();
    },

    /**
     * Приватный метод для инициализации обработчиков модальных окон.
     * @private
     */
    _initModalHandlers: function() {
        const self = this; // Сохраняем контекст `this`

        // --- ОБРАБОТЧИКИ ОТКРЫТИЯ ---
        $('#showRegisterModalBtn').on('click', () => {
            self.clearFormErrors(self.$registerModal);
            self.$registerModal.css('display', 'flex');
        });
        $('#showLoginModalBtn').on('click', () => {
            self.clearFormErrors(self.$loginModal);
            self.$loginModal.css('display', 'flex');
        });

        // --- ОБРАБОТЧИКИ ЗАКРЫТИЯ ---
        // 1. Закрытие по кнопкам "крестик" и "Готово"
        $('.close-modal-btn, #closeEditModalBtn').on('click', function() {
            const $modal = $(this).closest('.modal');
            if ($modal.is(self.$taskEditModal)) {
                self._closeEditModal();
            } else {
                $modal.css('display', 'none');
            }
        });

        // 2. Закрытие по клику на фон
        let mousedownOnBackdropFor = null;
        $('.modal').on('mousedown', function(event) {
            if (event.target === this) { mousedownOnBackdropFor = $(this); }
            else { mousedownOnBackdropFor = null; }
        });
        $('.modal').on('mouseup', function(event) {
            if (mousedownOnBackdropFor && mousedownOnBackdropFor.is($(this)) && event.target === this) {
                if ($(this).is(self.$taskEditModal)) {
                    self._closeEditModal();
                } else {
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
        window.eventBus.on('app:error', (event, errorData) => {
            if (errorData && errorData.message) {
                this.showToastNotification(errorData.message, 'error');
            }
        });
    },

    /**
     * Приватный метод для централизованного закрытия модального окна редактирования.
     * Выполняет все необходимые действия по очистке.
     * @private
     */
    _closeEditModal: function() {
        window.eventBus.off('.editModal');
        console.log('Event handlers for edit modal have been unbound.');
        this.$taskEditModal.css('display', 'none');
    },
    /**
     * Обновляет UI, чтобы отразить состояние "пользователь аутентифицирован".
     * @param {string} email - Email пользователя для отображения.
     */
    showLoggedInState: function (email) {
        this.$userEmailDisplay.text(email);
        this.$authContainer.hide();
        this.$userInfo.css('display', 'flex');
        window.tasksUi.show();
    },

    /**
     * Обновляет UI, чтобы отразить состояние "пользователь не аутентифицирован".
     */
    showLoggedOutState: function () {
        this.$userEmailDisplay.text('');
        this.$userInfo.hide();
        this.$authContainer.show();
        window.tasksUi.hide();
    },

    /**
     * Сбрасывает ВЕСЬ UI в начальное, "незалогиненное" состояние.
     * Скрывает все, что должно быть скрыто, показывает то, что должно быть видно.
     * Очищает все формы и сообщения об ошибках.
     */
    resetAllUIStateToLoggedOut: function() {
        // 1. Показываем/скрываем основные блоки
        this.showLoggedOutState();

        // 2. Очищаем все модальные формы
        this.clearFormErrors(this.$registerModal);
        this.$registerModal.find('form').trigger('reset'); // Сбрасываем значения полей

        this.clearFormErrors(this.$loginModal);
        this.$loginModal.find('form').trigger('reset');

        // 3. Очищаем все формы на основной странице
        window.tasksUi.clearCreateTaskForm(); // Делегируем очистку формы задач
    },

    /** Очищает все сообщения и подсветку ошибок внутри формы. */
    clearFormErrors: function ($form) {
        $form.find('.is-invalid').removeClass('is-invalid');
        $form.find('.field-error-message').text('');
        $form.find('.general-error-message').text('');
    },

    /** Показывает общую ошибку для формы. */
    showGeneralError: function ($form, message) {
        $form.find('.general-error-message').html(message); // .html() чтобы можно было вставить ссылку
    },

    /** Подсвечивает поля с ошибками валидации. */
    applyValidationErrors: function ($form, invalidParams) {
        if (!invalidParams || !Array.isArray(invalidParams)) return;

        invalidParams.forEach(error => {
            const fieldName = error.field;

            const $field = $form.find(`input[data-field-name="${fieldName}"]`);
            const $errorContainer = $form.find(`.field-error-message[data-field-name="${fieldName}"]`);

            if ($field.length) $field.addClass('is-invalid');
            if ($errorContainer.length) $errorContainer.text(error.message);

        });
    },

    /**
     * Показывает всплывающее toast-уведомление.
     * @param {string} message - Сообщение для отображения.
     * @param {string} type - Тип уведомления ('success', 'info', 'error', 'warning'), определяет цвет.
     */
    showToastNotification: function (message, type = 'error') {
        // Очищаем предыдущий таймер, если он был
        if (this.toastTimer) {
            clearTimeout(this.toastTimer);
        }

        this.$toastMessage.text(message);
        // Устанавливаем класс для цвета
        this.$toast.removeClass('success info error warning').addClass(type);
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
    checkInitialAuthState: function () {
        const token = localStorage.getItem('jwt_token');
        if (token) {
            return window.taskTrackerApi.getCurrentUser();
        } else {
            this.showLoggedOutState();
            return $.Deferred().reject().promise();
        }
    },

    /**
     * Блокирует кнопку отправки формы и показывает индикатор загрузки (опционально).
     * @param {jQuery} $form - jQuery-объект формы.
     */
    lockForm: function($form) {
        const $submitButton = $form.find('button[type="submit"]');
        if ($submitButton.length) {
            $submitButton.prop('disabled', true);
        }
    },

    /**
     * Разблокирует кнопку отправки формы и возвращает ее в исходное состояние.
     * @param {jQuery} $form - jQuery-объект формы.
     */
    unlockForm: function($form) {
        const $submitButton = $form.find('button[type="submit"]');
        if ($submitButton.length) {
            $submitButton.prop('disabled', false);
        }
    }
};