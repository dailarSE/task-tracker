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
                window.tasksStore._addOrUpdate(freshTask);
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
                _setupEditModalHandlers();
            })
            .fail(() => {
                window.tasksStore._remove(taskId);
                window.ui.showToastNotification('Не удалось загрузить задачу. Возможно, она была удалена.', 'error');
            });
    }

    /**
     * Привязывает все обработчики событий к элементам внутри модального окна.
     * Эта функция инкапсулирует всю сложную логику "live save" и обработки ошибок.
     * @private
     */
    function _setupEditModalHandlers() {
        const $form = $taskEditModal.find('form');
        const $titleInput = $taskEditModal.find('#editTaskTitle');
        const $descriptionInput = $taskEditModal.find('#editTaskDescription');
        const $statusCheckbox = $taskEditModal.find('#editTaskStatus');
        const $deleteBtn = $taskEditModal.find('#deleteTaskInModalBtn');

        let pendingChanges = {};
        let debounceTimer = null;
        const DEBOUNCE_DELAY = 750;

        let isSaving = false;
        let isConflictActive = false;
        let conflictRetryCount = 0;
        const MAX_CONFLICT_RETRIES = 2;

        function _debounce(func, delay) { clearTimeout(debounceTimer); debounceTimer = setTimeout(func, delay); }

        function _resetStateAfterConflict() {
            isConflictActive = false;
            conflictRetryCount = 0;
            pendingChanges = {};
            window.tasksUi.hideConflictResolver();
            $titleInput.prop('disabled', false);
            $descriptionInput.prop('disabled', false);
            $statusCheckbox.prop('disabled', false);
        }

        function _handleConflict(failedPayload) {
            isConflictActive = true;
            conflictRetryCount++;

            $('#overwriteBtn, #revertBtn').off('.editModal');

            if (conflictRetryCount > MAX_CONFLICT_RETRIES) {
                window.ui.showToastNotification("Не удалось сохранить: слишком много конфликтов. Пожалуйста, закройте и снова откройте окно.", "error");
                _resetStateAfterConflict();
                return;
            }

            const taskId = $form.data('originalTask').id;
            window.tasks.ensureLatest(taskId)
                .done((freshTask) => {
                    const localState = {
                        title: $titleInput.val(),
                        description: $descriptionInput.val(),
                        status: $statusCheckbox.is(':checked') ? 'COMPLETED' : 'PENDING'
                    };
                    window.tasksUi.showConflictResolver(taskId,localState, freshTask);

                    $('#overwriteBtn').on('click.editModal', function() {
                        const dataToSend = { ...localState, version: freshTask.version };
                        _commitSinglePatch(dataToSend);
                    });
                    $('#revertBtn').on('click.editModal', function() {
                        $titleInput.val(freshTask.title);
                        $descriptionInput.val(freshTask.description || '');
                        $statusCheckbox.prop('checked', freshTask.status === 'COMPLETED');
                        $form.data('originalTask', freshTask);
                        window.tasksStore._addOrUpdate(freshTask);
                        _resetStateAfterConflict();
                    });
                })
                .fail(() => { window.ui.showToastNotification("Ошибка загрузки актуальных данных для разрешения конфликта.", "error"); });
        }

        function _commitSinglePatch(payload) {
            const taskId = $form.data('originalTask').id;
            isSaving = true;
            window.tasksUi.showSavingIndicator();

            return window.tasks.patch(taskId, payload)
                .done(updatedTask => {
                    $form.data('originalTask', updatedTask);
                    window.tasksUi.showSavedIndicator();
                    _resetStateAfterConflict(); // Сбрасываем состояние конфликта при успехе
                })
                .fail(jqXHR => {
                    window.tasksUi.hideSaveIndicator();
                    if (jqXHR.status === 409) {
                        _handleConflict(payload);
                    } else if (jqXHR.status === 400 && jqXHR.responseJSON?.invalidParams) {
                        window.ui.applyValidationErrors($form, jqXHR.responseJSON.invalidParams);
                    } else {
                        window.ui.showToastNotification("Не удалось сохранить.", "error");
                    }
                })
                .always(() => { isSaving = false; });
        }

        function _commitPendingChanges() {
            const originalTask = $form.data('originalTask');
            if (!originalTask || Object.keys(pendingChanges).length === 0) return $.Deferred().resolve().promise();

            const payload = { ...pendingChanges, version: originalTask.version };
            pendingChanges = {};

            return _commitSinglePatch(payload);
        }

        function _tryToCommitChanges() {
            if (isSaving || isConflictActive) return;
            _commitPendingChanges();
        }

        // --- Привязка обработчиков с неймспейсом '.editModal' ---
        $taskEditModal.find('#closeEditModalBtn, .close-modal-btn').on('click.editModal', _closeEditModal);

        let mousedownOnBackdrop = false;
        $taskEditModal.on('mousedown.editModal', function(event) { mousedownOnBackdrop = (event.target === this); })
            .on('mouseup.editModal', function(event) { if (mousedownOnBackdrop && event.target === this) { _closeEditModal(); } mousedownOnBackdrop = false; });

        $titleInput.on('input.editModal', function() {
            window.ui.clearFormErrors($form);
            pendingChanges.title = $(this).val();
            _debounce(_tryToCommitChanges, DEBOUNCE_DELAY);
        });

        $descriptionInput.on('input.editModal', function() {
            window.ui.clearFormErrors($form);
            pendingChanges.description = $(this).val();
            _debounce(_tryToCommitChanges, DEBOUNCE_DELAY);
        });

        $statusCheckbox.on('change.editModal', function() {
            if (isSaving || isConflictActive) {
                $(this).prop('checked', !$(this).is(':checked'));
                window.ui.showToastNotification("Пожалуйста, подождите завершения сохранения.", "info");
                return;
            }
            clearTimeout(debounceTimer);
            pendingChanges.status = $(this).is(':checked') ? 'COMPLETED' : 'PENDING';
            _commitPendingChanges().fail(() => {
                const taskInStore = window.tasksStore.get($form.data('originalTask').id);
                if (taskInStore) { $(this).prop('checked', taskInStore.status === 'COMPLETED'); }
            });
        });

        $deleteBtn.on('click.editModal', function() {
            const originalTask = $form.data('originalTask');
            if (!window.confirm(`Вы уверены, что хотите удалить задачу "${originalTask.title}"?`)) return;

            $(this).prop('disabled', true);
            window.tasks.delete(originalTask.id)
                .done(() => { _closeEditModal(); window.ui.showToastNotification("Задача удалена.", "success"); })
                .fail(() => { $(this).prop('disabled', false); window.ui.showToastNotification("Не удалось удалить задачу.", "error"); });
        });
    }

    // --- ГЛАВНЫЙ ТРИГГЕР: Клик по заголовку задачи в списке ---
    $tasksContainer.on('click', '.task-title', function() {
        const taskId = $(this).closest('li').data('taskId');
        _openEditModal(taskId);
    });
}