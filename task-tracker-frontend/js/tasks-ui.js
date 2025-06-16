/**
 * Модуль для управления UI-компонентами, связанными с задачами.
 */
window.tasksUi = {
    // --- Кэшированные элементы UI ---
    $tasksContainer: $('#tasksContainer'),
    $createTaskForm: $('#createTaskForm'),
    $newTaskTitleInput: $('#newTaskTitle'),
    $undoneTasksList: $('#undoneTasksList'),
    $doneTasksList: $('#doneTasksList'),

    /**
     * Инициализирует подписчики на события.
     */
    init: function() {
        window.eventBus.on('tasks:refreshed', (e, tasks) => this.renderTaskLists(tasks));
        window.eventBus.on('task:updated', (e, task) => this.renderOrUpdateTask(task));
        window.eventBus.on('task:deleted', (e, taskData) => this.removeTaskElement(taskData.id));
    },

    show: function () {
        this.$tasksContainer.show();
    },
    hide: function () {
        this.$tasksContainer.hide();
    },

    /**
     * Создает HTML-разметку для одной задачи.
     * @param {object} task - Объект задачи, полученный от API.
     * @returns {string} HTML-строка для <li> элемента.
     */
    renderTask: function (task) {
        const isDone = task.status === 'COMPLETED';
        const checkedAttr = isDone ? 'checked' : '';
        const doneClass = isDone ? 'class="done"' : '';
        const escapedTitle = $('<div/>').text(task.title).html();

        return `<li data-task-id="${task.id}" ${doneClass}>
                    <label>
                        <input type="checkbox" class="task-checkbox" ${checkedAttr}>
                    </label>
                    <span class="task-title">${escapedTitle}</span>
                    <button class="delete-task-btn" aria-label="Delete task">×</button>
                </li>`;
    },

    /**
     * Полностью перерисовывает оба списка задач.
     * @param {Array<object>} tasks - Массив всех задач.
     */
    renderTaskLists: function(tasks) {
        this.$undoneTasksList.empty();
        this.$doneTasksList.empty();
        if (tasks) {
            tasks.forEach(task => {
                const taskHtml = this.renderTask(task);
                const $list = task.status === 'COMPLETED' ? this.$doneTasksList : this.$undoneTasksList;
                $list.append(taskHtml);
            });
            this.sortTaskList(this.$undoneTasksList);
            this.sortTaskList(this.$doneTasksList);
        }
    },

    /**
     * Обновляет одну задачу в UI.
     * Если статус изменился - перемещает элемент.
     * Если нет - просто перерисовывает его на месте.
     * Если элемента нет - добавляет его.
     * @param {object} updatedTask - Обновленный объект задачи из Store.
     */
    renderOrUpdateTask: function(updatedTask) {
        const $existingItem = $(`li[data-task-id="${updatedTask.id}"]`);

        if ($existingItem.length) {
            // --- Элемент уже существует, решаем, что с ним делать ---
            const isCurrentlyDone = $existingItem.parent().is(this.$doneTasksList);
            const shouldBeDone = updatedTask.status === 'COMPLETED';

            if (isCurrentlyDone !== shouldBeDone) {
                // СТАТУС ИЗМЕНИЛСЯ - ПЕРЕМЕЩАЕМ
                this._moveTaskElement($existingItem, shouldBeDone);
            } else {
                // Статус не изменился, просто перерисовываем на месте
                const taskHtml = this.renderTask(updatedTask);
                $existingItem.replaceWith(taskHtml);
            }
        } else {
            // --- Элемента нет, добавляем его ---
            const taskHtml = this.renderTask(updatedTask);
            const $list = updatedTask.status === 'COMPLETED' ? this.$doneTasksList : this.$undoneTasksList;
            $list.prepend(taskHtml); // Вставляем в начало
            this.sortTaskList($list); // и сортируем список
        }
    },

    /**
     * Приватный метод для анимированного перемещения элемента задачи между списками.
     * @param {jQuery} $taskItem - jQuery-объект <li> для перемещения.
     * @param {boolean} shouldBeDone - true, если задача должна быть в списке выполненных.
     * @private
     */
    _moveTaskElement: function($taskItem, shouldBeDone) {
        const $targetList = shouldBeDone ? this.$doneTasksList : this.$undoneTasksList;
        const self = this;

        $taskItem.fadeOut(200, function() {
            const $movedItem = $(this);
            // Обновляем классы и состояние чекбокса перед показом
            $movedItem.find('.task-checkbox').prop('checked', shouldBeDone);
            if (shouldBeDone) {
                $movedItem.addClass('done');
            } else {
                $movedItem.removeClass('done');
            }

            $targetList.prepend($movedItem);
            self.sortTaskList($targetList);
            $movedItem.fadeIn(200);
        });
    },

    /**
     * Сортирует задачи внутри указанного jQuery-элемента списка.
     * Сортировка происходит по ID задачи (data-task-id) в порядке убывания (новые вверху).
     * @param {jQuery} $list - jQuery-объект <ul> для сортировки.
     */
    sortTaskList: function ($list) {
        const items = $list.children('li:not(.placeholder-item)');
        const itemsArray = items.get();
        itemsArray.sort(function (a, b) {
            const idA = parseInt(a.getAttribute('data-task-id'), 10);
            const idB = parseInt(b.getAttribute('data-task-id'), 10);
            return idB - idA; // Сортировка по убыванию ID
        });
        $list.append(itemsArray);
    },

    removeTaskElement: function(taskId) {
        $(`li[data-task-id="${taskId}"]`).fadeOut(200, function() { $(this).remove(); });
    },

    clearCreateTaskForm: function () {
        window.ui.clearFormErrors(this.$createTaskForm);
        this.$newTaskTitleInput.val('');
    }
};