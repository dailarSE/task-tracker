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

        // Вызываем API для создания задачи
        window.taskTrackerApi.createTask({ title: title })
            .done((newTask) => {
                console.log('Task created successfully:', newTask);
                window.ui.showToastNotification('Задача добавлена!', 'success');
                window.ui.clearFormErrors(window.tasksUi.$createTaskForm);
                $newTaskTitleInput.val(''); // Очищаем поле ввода

                const taskHtml = window.tasksUi.renderTask(newTask);
                window.tasksUi.$undoneTasksList.prepend(taskHtml); // Добавляем в начало списка невыполненных
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
            });
    });
}