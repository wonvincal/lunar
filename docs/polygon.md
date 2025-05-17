https://polygon.io/knowledge-base/article/how-to-get-started-with-s3

# Configure your S3 Access and Secret keys
aws configure set aws_access_key_id Your-Access-Key
aws configure set aws_secret_access_key Your-Secret-Key

# List
aws s3 ls s3://flatfiles/ --endpoint-url https://files.polygon.io

# Copy
aws s3 cp s3://flatfiles/us_stocks_sip/trades_v1/2024/03/2024-03-07.csv.gz . --endpoint-url https://files.polygon.io