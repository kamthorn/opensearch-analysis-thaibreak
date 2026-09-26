#!/bin/bash
set -e

OPENSEARCH_URL="http://localhost:9200"

echo "Waiting for OpenSearch at ${OPENSEARCH_URL}..."
until curl -s "${OPENSEARCH_URL}/_cluster/health" > /dev/null; do
    sleep 2
done
echo "OpenSearch is ready!"

echo "1. Creating Thai Search Index with All Thaibreak Filters..."
curl -s -X DELETE "${OPENSEARCH_URL}/thai_showcase" > /dev/null || true

curl -s -X PUT "${OPENSEARCH_URL}/thai_showcase" -H 'Content-Type: application/json' -d'
{
  "settings": {
    "analysis": {
      "filter": {
        "thai_keyboard": {
          "type": "thaibreak_keyboard",
          "keep_original": true
        },
        "thai_soundex": {
          "type": "thaibreak_soundex",
          "keep_original": true
        },
        "thai_tone": {
          "type": "thaibreak_tone",
          "keep_original": true
        },
        "thai_number": {
          "type": "thaibreak_number",
          "keep_original": true
        }
      },
      "analyzer": {
        "thai_advanced": {
          "type": "custom",
          "tokenizer": "thaibreak",
          "filter": [
            "lowercase",
            "thai_keyboard",
            "thai_soundex",
            "thai_tone",
            "thai_number"
          ]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "title": {
        "type": "text",
        "analyzer": "thai_advanced"
      },
      "price": {
        "type": "text",
        "analyzer": "thai_advanced"
      }
    }
  }
}' | jq .

echo ""
echo "2. Indexing sample Thai documents..."
curl -s -X POST "${OPENSEARCH_URL}/thai_showcase/_doc/1" -H 'Content-Type: application/json' -d'
{
  "title": "โรงเรียนอนุบาลในกรุงเทพมหานคร",
  "price": "ค่าเทอมห้าหมื่นบาท"
}' | jq .

curl -s -X POST "${OPENSEARCH_URL}/thai_showcase/_doc/2" -H 'Content-Type: application/json' -d'
{
  "title": "ยินดีต้อนรับทุกท่านนะคะ",
  "price": "ราคา ๑๒๕๐ บาท"
}' | jq .

curl -s -X POST "${OPENSEARCH_URL}/thai_showcase/_doc/3" -H 'Content-Type: application/json' -d'
{
  "title": "เหตุการณ์สำคัญในประวัติศาสตร์",
  "price": "สองแสนห้าหมื่น"
}' | jq .

curl -s -X POST "${OPENSEARCH_URL}/thai_showcase/_refresh" > /dev/null

echo ""
echo "3. Testing Queries:"
echo "--- Query A: Search '50000' (Matching Thai word 'ห้าหมื่น') ---"
curl -s -X GET "${OPENSEARCH_URL}/thai_showcase/_search" -H 'Content-Type: application/json' -d'
{
  "query": { "match": { "price": "50000" } }
}' | jq '.hits.hits[] | {id: ._id, title: ._source.title, price: ._source.price}'

echo ""
echo "--- Query B: Search 'นะค่ะ' (Loose tone match for 'นะคะ') ---"
curl -s -X GET "${OPENSEARCH_URL}/thai_showcase/_search" -H 'Content-Type: application/json' -d'
{
  "query": { "match": { "title": "นะค่ะ" } }
}' | jq '.hits.hits[] | {id: ._id, title: ._source.title}'

echo ""
echo "--- Query C: Search 'อนุบาล' (Subword decompounding match for 'โรงเรียนอนุบาล') ---"
curl -s -X GET "${OPENSEARCH_URL}/thai_showcase/_search" -H 'Content-Type: application/json' -d'
{
  "query": { "match": { "title": "อนุบาล" } }
}' | jq '.hits.hits[] | {id: ._id, title: ._source.title}'

echo ""
echo "--- Query D: Search '1250' (Matching Thai digits '๑๒๕๐') ---"
curl -s -X GET "${OPENSEARCH_URL}/thai_showcase/_search" -H 'Content-Type: application/json' -d'
{
  "query": { "match": { "price": "1250" } }
}' | jq '.hits.hits[] | {id: ._id, title: ._source.title, price: ._source.price}'

echo ""
echo "--- Query E: Search 'การ' (Soundex homophone match for 'การณ์') ---"
curl -s -X GET "${OPENSEARCH_URL}/thai_showcase/_search" -H 'Content-Type: application/json' -d'
{
  "query": { "match": { "title": "การ" } }
}' | jq '.hits.hits[] | {id: ._id, title: ._source.title}'

echo ""
echo "All tests completed successfully!"
