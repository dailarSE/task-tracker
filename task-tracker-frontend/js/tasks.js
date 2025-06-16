/**
 * @file tasks.js
 * @description
 * Этот файл является слоем бизнес-логики ("команд") и управления UI-событиями для задач.
 * Он оркестрирует взаимодействие между UI (через обработчики событий),
 * API-слоем (window.taskTrackerApi) и клиентским хранилищем состояния (window.tasksStore).
 * Все асинхронные операции и сложная логика жизненного цикла UI инкапсулированы здесь.
 */

/**
 * Глобальный объект `tasks`, содержащий "команды" для манипуляции задачами.
 * Каждая команда представляет собой одну логическую операцию, которая вызывает API,
 * и при успехе обновляет клиентский Store.
 * @namespace tasks
 */
window.tasks = {
    /**
     * Загружает все задачи для текущего пользователя с сервера и инициализирует tasksStore.
     * @returns {Promise} jQuery Promise от API-вызова.
     */
    loadAll: function() {
        return window.taskTrackerApi.getTasks()
            .done(tasks => {
                window.tasksStore._init(tasks);
            });
    },

    /**
     * Отправляет команду на создание новой задачи.
     * @param {object} createData - Данные для создания задачи. Ожидается { title: string, description?: string }.
     * @returns {Promise} jQuery Promise от API-вызова.
     */
    create: function(createData) {
        return window.taskTrackerApi.createTask(createData)
            .done(newTask => {
                window.tasksStore._addOrUpdate(newTask);
            });
    },

    /**
     * Отправляет команду на частичное обновление задачи.
     * @param {number} taskId - ID задачи для обновления.
     * @param {object} patchData - Объект с измененными полями и обязательным полем 'version'.
     * @returns {Promise} jQuery Promise от API-вызова.
     */
    patch: function(taskId, patchData) {
        return window.taskTrackerApi.patchTask(taskId, patchData)
            .done(updatedTask => {
                window.tasksStore._addOrUpdate(updatedTask);
            });
    },

    /**
     * Отправляет команду на удаление задачи.
     * @param {number} taskId - ID задачи для удаления.
     * @returns {Promise} jQuery Promise от API-вызова.
     */
    delete: function(taskId) {
        return window.taskTrackerApi.deleteTask(taskId)
            .done(() => {
                window.tasksStore._remove(taskId);
            });
    },

    /**
     * Загружает актуальную версию задачи с сервера и обновляет Store.
     * Эта команда используется для синхронизации перед началом редактирования.
     * @param {number} taskId - ID задачи.
     * @returns {Promise} jQuery Promise от API-вызова.
     */
    ensureLatest: function(taskId) {
        return window.taskTrackerApi.getTaskById(taskId)
            .done((freshTask) => {
                this._addOrUpdate(freshTask);
            });
    }
};

/**
 * Инициализирует все обработчики UI-событий, связанных с задачами.
 * Эта функция должна вызываться один раз при старте приложения.
 */
function setupTaskHandlers() {
    const $tasksContainer = $('#tasksContainer');
    const $createTaskForm = $('#createTaskForm');
    const $newTaskTitleInput = $('#newTaskTitle');
    const $taskEditModal = $('#taskEditModal');

    // --- Обработчик для формы СОЗДАНИЯ ЗАДАЧИ ---
    $createTaskForm.on('submit', (event) => {
        event.preventDefault();
        window.ui.clearFormErrors($createTaskForm);

        const title = $newTaskTitleInput.val().trim();
        if (!title) {
            window.ui.applyValidationErrors($createTaskForm, [{ field: 'title', message: 'Title is required' }]);
            return;
        }

        window.ui.lockForm($createTaskForm);
        window.tasks.create({ title: title })
            .done(() => {
                $newTaskTitleInput.val('');
                window.ui.showToastNotification('Задача добавлена!', 'success');
            })
            .fail((jqXHR) => {
                const problem = jqXHR.responseJSON;
                const errorMessage = problem?.detail || problem?.title || 'Failed to create task.';
                window.ui.showGeneralError($createTaskForm, errorMessage);
                if (problem?.invalidParams) {
                    window.ui.applyValidationErrors($createTaskForm, problem.invalidParams);
                }
            })
            .always(() => {
                window.ui.unlockForm($createTaskForm);
            });
    });

    // --- Обработчик для ИЗМЕНЕНИЯ СТАТУСА ЗАДАЧИ ---
    $tasksContainer.on('change', '.task-checkbox', function(event) {
        const $checkbox = $(this);
        const $listItem = $checkbox.closest('li');
        if ($listItem.attr('data-processing')) { event.preventDefault(); return; }

        $listItem.attr('data-processing', true);
        $checkbox.prop('disabled', true);

        const taskId = $listItem.data('taskId');
        const newStatus = $checkbox.is(':checked') ? 'COMPLETED' : 'PENDING';

        const currentTask = window.tasksStore.get(taskId);
        if (!currentTask) {
            console.error(`Task with ID ${taskId} not found in store for status update.`);
            $checkbox.prop('disabled', false);
            $listItem.removeAttr('data-processing');
            return;
        }

        const patchData = { status: newStatus, version: currentTask.version };
        window.tasks.patch(taskId, patchData)
            .fail(() => {
                $checkbox.prop('checked', !$checkbox.is(':checked'));
                window.ui.showToastNotification('Could not update task status.', 'error');
            })
            .always(() => {
                $checkbox.prop('disabled', false);
                $listItem.removeAttr('data-processing');
            });
    });

    // --- Обработчик для УДАЛЕНИЯ ЗАДАЧИ ---
    $tasksContainer.on('click', '.delete-task-btn', function() {
        const $button = $(this);
        const $listItem = $button.closest('li');
        if ($listItem.attr('data-processing')) return;

        const taskId = $listItem.data('taskId');
        if (!window.confirm("Вы уверены, что хотите удалить эту задачу?")) return;

        $listItem.attr('data-processing', true);
        $button.prop('disabled', true);

        window.tasks.delete(taskId)
            .done(() => {
                window.ui.showToastNotification('Задача успешно удалена.', 'success');
            })
            .fail((jqXHR) => {
                if (jqXHR.status === 404) {
                    window.tasksStore._remove(taskId);
                    window.ui.showToastNotification('Задача уже была удалена.', 'info');
                } else {
                    const problem = jqXHR.responseJSON;
                    const message = problem?.detail || 'Could not delete the task.';
                    window.ui.showToastNotification(message, 'error');
                    $button.prop('disabled', false);
                }
            })
            .always(() => {
                if ($listItem.length) { $listItem.removeAttr('data-processing'); }
            });
    });

    // ===================================================================
    // == ЛОГИКА МОДАЛЬНОГО ОКНА РЕДАКТИРОВАНИЯ
    // ===================================================================

    /**
     * Закрывает модальное окно редактирования и очищает все его обработчики.
     * @private
     */
    function _closeEditModal() {
        $taskEditModal.find('*').off('.editModal');
        $taskEditModal.off('.editModal');
        console.log('DOM event handlers for edit modal have been unbound.');
        $taskEditModal.css('display', 'none');
    }

    /**
     * Открывает модальное окно редактирования для задачи.
     * @param {number} taskId - ID задачи для редактирования.
     * @private
     */
    function _openEditModal(taskId) {
        window.ui.showToastNotification('Загрузка...', 'info');

        window.tasks.ensureLatest(taskId)
            .done((freshTask) => {
                window.tasksUi.showEditModal(freshTask);
                _setupEditModalHandlers(freshTask); // Привязываем обработчики
            })
            .fail(() => {
                window.tasksStore._remove(taskId);
                window.ui.showToastNotification('Не удалось загрузить задачу. Возможно, она была удалена.', 'error');
            });
    }

    /**
     * Привязывает все обработчики событий к элементам внутри модального окна.
     * @param {object} task - Объект задачи, для которой открыто окно.
     * @private
     */
    function _setupEditModalHandlers(task) {
        console.log(`Setting up handlers for task ${task.id}`);

        // --- Закрытие окна ---
        $taskEditModal.find('#closeEditModalBtn, .close-modal-btn').on('click.editModal', _closeEditModal);

        let mousedownOnBackdrop = false;
        $taskEditModal.on('mousedown.editModal', function(event) {
            mousedownOnBackdrop = (event.target === this);
        }).on('mouseup.editModal', function(event) {
            if (mousedownOnBackdrop && event.target === this) { _closeEditModal(); }
            mousedownOnBackdrop = false;
        });

        // --- Обработчики действий ---
        _setupLiveSaveHandlers();
        _setupEditModalActions();
    }

    /**
     * Настраивает "live save" для полей title и description в модальном окне.
     * Использует debounce для предотвращения частых запросов и батчинг
     * для отправки всех изменений одним запросом.
     * @private
     */
    function _setupLiveSaveHandlers() {
        const $form = $taskEditModal.find('form');
        const $titleInput = $taskEditModal.find('#editTaskTitle');
        const $descriptionInput = $taskEditModal.find('#editTaskDescription');

        let pendingChanges = {}; // Объект для накопления изменений
        let debounceTimer = null;
        const DEBOUNCE_DELAY = 750; // мс

        let isSaving = false;
        let hasUnsentChanges = false;

        /**
         * Утилита debounce.
         */
        function _debounce(func, delay) {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(func, delay);
        }

        /**
         * Пытается запустить отправку изменений.
         * Если сохранение уже идет, просто выставляет флаг.
         */
        function _tryToCommitChanges() {
            if (isSaving) {
                hasUnsentChanges = true;
                console.log("Save in progress. Queuing subsequent changes.");
                return;
            }
            _commitPendingChanges();
        }

        /**
         * Отправляет накопленные изменения на сервер.
         */
        function _commitPendingChanges() {
            const originalTask = $form.data('originalTask');
            if (!originalTask || Object.keys(pendingChanges).length === 0) {
                return; // Нечего отправлять
            }

            isSaving = true;
            window.tasksUi.showSavingIndicator();

            const changesToSend = { ...pendingChanges };
            pendingChanges = {};
            hasUnsentChanges = false;

            const taskId = originalTask.id;
            const payload = { ...changesToSend, version: originalTask.version };


            window.tasks.patch(taskId, payload)
                .done((updatedTask) => {
                    console.log(`Task ${taskId} saved successfully. New version: ${updatedTask.version}`);
                    // 1. Обновляем наш "якорь" с последней успешной версией
                    $form.data('originalTask', updatedTask);
                    // 2. Показываем фидбэк
                    window.tasksUi.showSavedIndicator();
                    // 3. Store уже обновился внутри tasks.patch().done()
                })
                .fail((jqXHR) => {
                    // TODO: FT-LS-03, FT-LS-04 - Обработка ошибок
                    console.error("Live save failed:", jqXHR);
                    window.tasksUi.hideSaveIndicator();
                    // Возвращаем неотправленные изменения обратно в `pendingChanges`,
                    // чтобы пользователь не потерял их.
                    pendingChanges = { ...changesToSend, ...pendingChanges };
                })
                .always(() => {
                    isSaving = false;
                    // Если за время запроса накопились новые изменения,
                    // сразу же пытаемся их отправить.
                    if (hasUnsentChanges) {
                        _tryToCommitChanges();
                    }
                });
        }

        // --- Привязка обработчиков ---
        $titleInput.on('input.editModal', () => {
            const originalTask = $form.data('originalTask');
            const currentValue = $titleInput.val();
            if (originalTask.title !== currentValue) {
                pendingChanges.title = currentValue;
                _debounce(_tryToCommitChanges, DEBOUNCE_DELAY);
            }
        });

        $descriptionInput.on('input.editModal', () => {
            const originalTask = $form.data('originalTask');
            const currentValue = $descriptionInput.val();
            if (originalTask.description !== currentValue) {
                pendingChanges.description = currentValue;
                _debounce(_tryToCommitChanges, DEBOUNDE_DELAY);
            }
        });
    }

    /**
     * Настраивает действия для кнопок внутри модального окна (статус, удаление).
     * @private
     */
    function _setupEditModalActions() {
        // TODO: Реализовать обработчики для чекбокса статуса и кнопки удаления.
        console.log('TODO: Implement _setupEditModalActions');
    }

    // --- ГЛАВНЫЙ ТРИГГЕР: Клик по заголовку задачи в списке ---
    $tasksContainer.on('click', '.task-title', function() {
        const taskId = $(this).closest('li').data('taskId');
        _openEditModal(taskId);
    });
}