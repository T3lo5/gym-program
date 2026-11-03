# Atalhos de verificação (rode `make verify` antes de commitar)
.PHONY: verify verify-android verify-backend build clean

verify:
	./scripts/verify.sh

verify-android:
	SKIP_BACKEND=1 ./scripts/verify.sh

verify-backend:
	./scripts/ci-backend.sh

build:
	./scripts/build-docker.sh :app:assembleDebug

clean:
	rm -rf dist .gradle-docker
