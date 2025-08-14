import pika
import time
import sys


RABBITMQ_URL = "amqp://guest:guest@rabbitmq-service.default.svc.cluster.local:5672"
QUEUE_NAME = "hello"

LOG_FILE_PATH = "/data/messages.log"

print("Consumer init...")

connection = None

for i in range(10):
    try:
        print("Attempting to connect to RabbitMQ...")
        connection = pika.BlockingConnection(pika.URLParameters(RABBITMQ_URL))

        print("Connected!")
        break
    except pika.exceptions.AMQPConnectionError as e:
        print("Connection failed")
        print("Retrying in 5 seconds...")
        time.sleep(5)

if not connection:
    print("Failed to connect to RabbitMQ")
    sys.exit(1)

def callback(ch, method, properties, body):
    message = body.decode()
    print("Received message: " + message)

    try:

        with open(LOG_FILE_PATH, 'a') as f:
            f.write(message + '\n')
        print("Wrote message to file")

        time.sleep(1) #Simulate work

        ch.basic_ack(delivery_tag=method.delivery_tag)
        print("Acknowledged message")
    
    except Exception as e:
        print(f"Something went wrong: {e}")

channel = connection.channel()

channel.basic_qos(prefetch_count=1) #fetch only one message per time
channel.basic_consume(queue=QUEUE_NAME, on_message_callback=callback)

channel.start_consuming()