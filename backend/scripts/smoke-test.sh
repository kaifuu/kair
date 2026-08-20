#!/usr/bin/env bash
# 升级后全流程冒烟:登录/RBAC/设备API/401
set -e
BASE=http://127.0.0.1:8180/api

echo "== 1. admin 登录 =="
CAP=$(curl -s $BASE/auth/captcha)
CID=$(echo "$CAP" | sed -n 's/.*"cid":"\([^"]*\)".*/\1/p')
CODE=$(echo "$CAP" | grep -o "<text[^>]*>[A-Z0-9]</text>" | sed 's/<[^>]*>//g' | tr -d '\n')
echo "captcha=$CODE"
LOGIN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"admin123\",\"cid\":\"$CID\",\"captcha\":\"$CODE\"}")
TOKEN=$(echo "$LOGIN" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
echo "token=${TOKEN:0:16}..."
echo "$LOGIN" | grep -o '"roleCode":"[^"]*"'

echo "== 2. admin 菜单(应含系统管理) =="
curl -s $BASE/menus/mine -H "Authorization: Bearer $TOKEN" \
  | grep -o '"group":"[A-Z]*"' | sort | uniq -c

echo "== 3. 设备列表 =="
curl -s "$BASE/devices" -H "Authorization: Bearer $TOKEN" \
  | grep -o '"code":"[^"]*"' | head -12

echo "== 4. operator 登录(应只见 BIZ) =="
CAP=$(curl -s $BASE/auth/captcha)
CID=$(echo "$CAP" | sed -n 's/.*"cid":"\([^"]*\)".*/\1/p')
CODE=$(echo "$CAP" | grep -o "<text[^>]*>[A-Z0-9]</text>" | sed 's/<[^>]*>//g' | tr -d '\n')
LOGIN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"operator\",\"password\":\"operator123\",\"cid\":\"$CID\",\"captcha\":\"$CODE\"}")
OTOKEN=$(echo "$LOGIN" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -s $BASE/menus/mine -H "Authorization: Bearer $OTOKEN" \
  | grep -o '"group":"[A-Z]*"' | sort | uniq -c

echo "== 5. 错误密码(应 401 业务码) =="
CAP=$(curl -s $BASE/auth/captcha)
CID=$(echo "$CAP" | sed -n 's/.*"cid":"\([^"]*\)".*/\1/p')
CODE=$(echo "$CAP" | grep -o "<text[^>]*>[A-Z0-9]</text>" | sed 's/<[^>]*>//g' | tr -d '\n')
curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"wrong\",\"cid\":\"$CID\",\"captcha\":\"$CODE\"}" | head -c 120; echo

echo "== 6. 伪造 token(应 401) =="
curl -s -o /dev/null -w "http=%{http_code}\n" $BASE/devices -H "Authorization: Bearer faketoken123"

echo "== 7. 日志(登录成功+失败已产生) =="
curl -s "$BASE/logs?type=LOGIN&size=5" -H "Authorization: Bearer $TOKEN" | head -c 300; echo
