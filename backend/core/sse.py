"""SSE 스트리밍 인코딩 헬퍼."""


def sse_data(payload: str) -> str:
    """SSE data 라인 1개로 인코딩한다.

    payload에 개행이 있으면 'data: ' 접두사 없는 줄이 생겨 클라이언트가
    그 뒷부분을 통째로 버리므로, 개행을 리터럴 '\\n'으로 이스케이프해
    반드시 한 줄로 보낸다. (앱은 수신 시 '\\n' -> 개행으로 복원한다)
    """
    return "data: " + payload.replace("\r", "").replace("\n", "\\n") + "\n\n"
