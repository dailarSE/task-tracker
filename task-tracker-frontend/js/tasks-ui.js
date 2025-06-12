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
                        <span>${escapedTitle}</span>
                    </label>
                </li>`;
    },

    /**
     * Очищает и заполняет списки задач на странице.
     * @param {Array<object>} tasks - Массив объектов задач.
     */
    renderTaskLists: function (tasks) {
        // Очищаем оба списка перед рендерингом
        this.$undoneTasksList.empty();
        this.$doneTasksList.empty();

        if (tasks) {
            tasks.forEach(task => {
                const taskHtml = this.renderTask(task);
                if (task.status === 'COMPLETED') {
                    this.$doneTasksList.append(taskHtml);
                } else {
                    this.$undoneTasksList.append(taskHtml);
                }
            });
        }
    },

    /**
     * Динамически перемещает элемент задачи и сортирует списки ПОСЛЕ анимации.
     * @param {number} taskId - ID задачи для перемещения.
     * @param {string} newStatus - Новый статус ('PENDING' или 'COMPLETED').
     */
    moveTaskElement: function(taskId, newStatus) {
        const $taskItem = $(`li[data-task-id="${taskId}"]`);
        if ($taskItem.length === 0) return;

        const isCompleted = newStatus === 'COMPLETED';
        const $targetList = isCompleted ? this.$doneTasksList : this.$undoneTasksList;
        const self = this;

        $taskItem.fadeOut(200, function() {
            const $movedItem = $(this);
            $movedItem.hide();

            $targetList.prepend($movedItem);

            self.sortTaskList($targetList);

            if (isCompleted) {
                $movedItem.addClass('done');
            } else {
                $movedItem.removeClass('done');
            }
            $movedItem.fadeIn(200);
        });
    },

    /**
     * Сортирует задачи внутри указанного jQuery-элемента списка.
     * Сортировка происходит по ID задачи (data-task-id) в порядке убывания (новые вверху).
     * @param {jQuery} $list - jQuery-объект <ul> для сортировки.
     */
    sortTaskList: function($list) {
        const items = $list.children('li:not(.placeholder-item)');
        const itemsArray = items.get();
        itemsArray.sort(function(a, b) {
            const idA = parseInt(a.getAttribute('data-task-id'), 10);
            const idB = parseInt(b.getAttribute('data-task-id'), 10);
            return idB - idA; // Сортировка по убыванию ID
        });
        $list.append(itemsArray);
    },

    clearCreateTaskForm: function () {
        window.ui.clearFormErrors(this.$createTaskForm);
        this.$newTaskTitleInput.val('');
    }
};