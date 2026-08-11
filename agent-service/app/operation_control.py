from collections.abc import Callable

ProgressCallback = Callable[[int, int, str], None]
CancelCheck = Callable[[], bool]


class OperationCancelled(RuntimeError):
    pass


def ensure_not_cancelled(cancel_check: CancelCheck | None) -> None:
    if cancel_check and cancel_check():
        raise OperationCancelled("operation cancelled")


def report_progress(
    callback: ProgressCallback | None, current: int, total: int, message: str
) -> None:
    if callback:
        callback(current, total, message)
