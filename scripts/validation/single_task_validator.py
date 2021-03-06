"""
Validates if the experiment events meet straightforward conditions like:
  * task started before ended
  * transfer started before ended
  * vm was launched before being terminated
  * task/transfer/vm was started and was finished
"""

from log_parser.execution_log import EventType
from validation.common import ValidationResult


MISSING_VALUE = None


def validate_experiment(execution_log):
    tasks = execution_log.events[EventType.TASK]
    transfers = execution_log.events[EventType.TRANSFER]
    vms = execution_log.events[EventType.VM]

    task_errors = get_errors([validate_task(task) for task in tasks])
    transfer_errors = get_errors([validate_transfer(transfer) for transfer in transfers])
    vm_errors = get_errors([validate_vm(vm) for vm in vms])

    return ValidationResult(task_errors + transfer_errors + vm_errors)


def get_errors(errors):
    return [error.message for error in errors if not error.is_valid]


class EventValidationResult(object):
    def __init__(self, is_valid, message=''):
        self.is_valid = is_valid
        self.message = message


def validate_task(task):
    if task.started == MISSING_VALUE:
        return EventValidationResult(False, 'job {} hasn\'t started computation at all'.format(task.id))

    if task.finished == MISSING_VALUE:
        return EventValidationResult(False, 'job {} hasn\'t finished computation at all'.format(task.id))

    if not task.started <= task.finished:
        return EventValidationResult(False, 'job {} didn\'t hold time order'.format(task.id))

    return EventValidationResult(True)


def validate_transfer(transfer):
    if transfer.started == MISSING_VALUE:
        return EventValidationResult(False, 'transfer {} hasn\'t started at all'.format(transfer.id))

    if transfer.finished == MISSING_VALUE:
        return EventValidationResult(False, 'transfer {} hasn\'t finished at all'.format(transfer.id))

    if not transfer.started <= transfer.finished:
        return EventValidationResult(False, 'transfer {} didn\'t hold time order'.format(transfer.id))

    return EventValidationResult(True)


def validate_vm(vm):
    if vm.started == MISSING_VALUE:
        return EventValidationResult(False, 'VM {} hasn\'t been launched at all'.format(vm.id))

    if vm.finished == MISSING_VALUE:
        return EventValidationResult(False, 'VM {} hasn\'t been terminated at all'.format(vm.id))

    if not vm.started <= vm.finished:
        return EventValidationResult(False, 'VM {} provisioning didn\'t hold time order'.format(vm.id))

    return EventValidationResult(True)

