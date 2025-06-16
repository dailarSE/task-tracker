/**
 * @file tasks.js
 * @description
 * Этот файл является слоем бизнес-логики ("команд") для работы с задачами.
 * Он оркестрирует взаимодействие между UI (через обработчики событий),
 * API-слоем (window.taskTrackerApi) и клиентским хранилищем состояния (window.tasksStore).
 * Все асинхронные операции инкапсулированы здесь.
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
     * @returns {Promise} jQuery Promise от API-вызова, который можно использовать
     * для обработки ошибок на верхнем уровне (например, при инициализации приложения).
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
     * Отправляет команду на обновление только статуса задачи.
     * Является удобной оберткой над `tasks.patch`.
     * @param {number} taskId - ID задачи.
     * @param {'PENDING' | 'COMPLETED'} newStatus - Новый статус задачи.
     * @returns {Promise} jQuery Promise от API-вызова.
     */
    updateStatus: function(taskId, newStatus) {
        // Обертка над patch для удобства
        const currentTask = window.tasksStore.get(taskId);
        if (!currentTask) return $.Deferred().reject().promise();

        const patchData = { status: newStatus, version: currentTask.version };
        return this.patch(taskId, patchData);
    }
};

/**
 * Инициализирует все обработчики UI-событий, связанных с задачами.
 * Эта функция должна вызываться один раз при старте приложения.
 */
function setupTaskHandlers() {
    const $createTaskForm = window.tasksUi.$createTaskForm;
    const $newTaskTitleInput = window.tasksUi.$newTaskTitleInput;
    const $tasksContainer = $('#tasksContainer');

    // --- Обработчик для формы СОЗДАНИЯ ЗАДАЧИ ---
    $createTaskForm.on('submit', (event) => {
        event.preventDefault();

        // Очищаем предыдущие ошибки
        window.ui.clearFormErrors($createTaskForm);

        const title = $newTaskTitleInput.val().trim();

        // Простая клиентская валидация, чтобы не отправлять пустой запрос
        if (!title) {
            window.ui.applyValidationErrors($createTaskForm, [{
                field: 'title', // Используем data-field-name из HTML
                message: 'Title is required'
            }]);
            return;
        }

        window.ui.lockForm($createTaskForm);

        // Вызываем API для создания задачи
        window.tasks.create({title: title})
            .done((newTask) => {
                console.log('Task created successfully:', newTask);
                window.ui.showToastNotification('Задача добавлена!', 'success');
                window.ui.clearFormErrors(window.tasksUi.$createTaskForm);
                $newTaskTitleInput.val(''); // Очищаем поле ввода
            })
            .fail((jqXHR) => {
                const problem = jqXHR.responseJSON;
                console.error("Failed to create task:", problem);

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

    // --- Обработчик для ИЗМЕНЕНИЯ СТАТУСА ЗАДАЧИ (через делегирование) ---
    $tasksContainer.on('change', '.task-checkbox', function (event) {
        const $checkbox = $(this);
        const $listItem = $checkbox.closest('li');

        // 1. Проверяем, не выполняется ли уже операция.
        if ($listItem.attr('data-processing')) {
            event.preventDefault();
            return;
        }
        $listItem.attr('data-processing', true);

        const taskId = $listItem.data('taskId');

        // 1. Определяем новый статус
        const newStatus = $checkbox.is(':checked') ? 'COMPLETED' : 'PENDING';

        // 2. Блокируем чекбокс, чтобы предотвратить двойные клики
        $checkbox.prop('disabled', true);

        window.tasks.updateStatus(taskId, newStatus)
            .fail((jqXHR) => {
                console.error('Failed to update task status:', jqXHR.responseJSON);

                $checkbox.prop('checked', !$checkbox.is(':checked'));

                const problem = jqXHR.responseJSON;
                const message = problem?.detail || 'Could not update task status.';
                window.ui.showToastNotification(message, 'error');
            })
            .always(() => {
                // 4. В любом случае разблокируем чекбокс
                $checkbox.prop('disabled', false);
                $listItem.removeAttr('data-processing');
            });
    });

    // --- Обработчик для УДАЛЕНИЯ ЗАДАЧИ (через делегирование) ---
    $tasksContainer.on('click', '.delete-task-btn', function () {
        const $button = $(this);
        const $listItem = $button.closest('li');

        if ($listItem.attr('data-processing')) {
            return;
        }
        $listItem.attr('data-processing', true);

        const taskId = $listItem.data('taskId');

        // Запрашиваем подтверждение у пользователя
        const isConfirmed = window.confirm("Вы уверены, что хотите удалить эту задачу?");

        if (isConfirmed) {
            window.tasks.delete(taskId)
                .done(function() {
                    window.ui.showToastNotification('Задача успешно удалена.', 'success');
                })
                .fail(function(jqXHR) {
                    if (jqXHR.status === 404) {
                        window.tasksStore._remove(taskId);
                        window.ui.showToastNotification('Задача уже была удалена.', 'info');
                    } else {
                        console.error('Failed to delete task:', jqXHR.responseJSON);
                        const problem = jqXHR.responseJSON;
                        const message = problem?.detail || 'Could not delete the task.';
                        window.ui.showToastNotification(message, 'error');
                    }
                });
        }

        $listItem.removeAttr('data-processing');
    });
}