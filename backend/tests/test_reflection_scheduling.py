"""main.py의 reflection 배치 스케줄러 등록 — FastAPI startup 이벤트에 의존하지
않고 등록 함수 자체를 직접 테스트한다 (conftest의 client 픽스처는 startup 이벤트를
발화시키지 않는 것이 이 프로젝트 기존 관례)."""
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger


def test_register_reflection_job_uses_configured_hour():
    from core.config import settings
    from main import _register_reflection_job

    sched = AsyncIOScheduler()
    _register_reflection_job(sched)

    job = sched.get_job("nightly_reflection")
    assert job is not None
    assert isinstance(job.trigger, CronTrigger)
    hour_field = next(f for f in job.trigger.fields if f.name == "hour")
    assert str(hour_field) == str(settings.REFLECTION_HOUR)


def test_register_reflection_job_is_idempotent():
    from main import _register_reflection_job

    sched = AsyncIOScheduler()
    _register_reflection_job(sched)
    _register_reflection_job(sched)  # 두 번 호출해도 에러 없이 교체돼야 함

    assert len(sched.get_jobs()) == 1
