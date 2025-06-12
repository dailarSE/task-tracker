/**
 * Модуль для управления бизнес-логикой задач.
 * Инициализирует обработчики для создания, редактирования, удаления задач.
 */
function setupTaskHandlers() {
    const $createTaskForm = window.tasksUi.$createTaskForm;
    const $newTaskTitleInput = window.tasksUi.$newTaskTitleInput;

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
        window.taskTrackerApi.createTask({title: title})
            .done((newTask) => {
                console.log('Task created successfully:', newTask);
                window.ui.showToastNotification('Задача добавлена!', 'success');
                window.ui.clearFormErrors(window.tasksUi.$createTaskForm);
                $newTaskTitleInput.val(''); // Очищаем поле ввода

                const $list = window.tasksUi.$undoneTasksList;

                // Теперь добавляем новый элемент в (возможно) уже очищенный список.
                const taskHtml = window.tasksUi.renderTask(newTask);
                $list.prepend(taskHtml);
                window.tasksUi.sortTaskList($list);
            })
            .fail((jqXHR) => {
                const problem = jqXHR.responseJSON;
                console.error("Failed to create task:", problem);

                const errorMessage = problem?.detail || problem?.title || 'Failed to create task.';
                window.ui.showGeneralError($createTaskForm, errorMessage);

                // Затем ошибки по конкретным полям, если они есть
                if (problem?.invalidParams) {
                    window.ui.applyValidationErrors($createTaskForm, problem.invalidParams);
                }
            })
            .always(() => {
                window.ui.unlockForm($createTaskForm);
            });
    });

    // --- Обработчик для ИЗМЕНЕНИЯ СТАТУСА ЗАДАЧИ (через делегирование) ---
    $('#tasksContainer').on('change', '.task-checkbox', function (event) {
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

        window.taskTrackerApi.updateTaskStatus(taskId, newStatus)
            .done((updatedTask) => {
                console.log('Task status updated successfully:', updatedTask);
                window.tasksUi.moveTaskElement(taskId, updatedTask.status);
            })
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
}