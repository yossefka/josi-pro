import cv2
from keras.preprocessing import image
import os
from pygame import mixer
import random
import numpy as np
from playsound import playsound


mixer.init()
sound = mixer.Sound(r'C:\Users\Joss\PycharmProjects\pythonProject3\Untitled.wav')
sound1 = mixer.Sound(r'C:\Users\Joss\PycharmProjects\pythonProject3\not.wav')
# sound = mixer.Sound(r'C:\Users\Joss\PycharmProjects\pythonProject3\Edible.m4a')
# sound1 = mixer.Sound(r'C:\Users\Joss\PycharmProjects\pythonProject3\Not.m4a')

os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
categories = ['Edible fruit', 'Non_Edible fruit']
data = []
image_size = 224
for category in categories:
    path = os.path.join(r'C:\Users\Joss\PycharmProjects\pythonProject3\AugmentedDataset', category)
    label = categories.index(category)
    for file in os.listdir(path):
        imag_path = os.path.join(path, file)
        # print(imag_path)
        img = cv2.imread(imag_path)
        img = cv2.resize(img, (image_size, image_size))
        # print(img.shape)
        data.append([img, label])
        # print(len(data))

random.shuffle(data)
x = []
y = []
for feature, label in data:
    x.append(feature)
    y.append(label)
# print(len(x))
# print(len(y))

x = np.array(x)
y = np.array(y)
# print(x.shape)
# print(y.shape)
x = x/255
#print(x)


from sklearn.model_selection import train_test_split
X_train, X_test, y_train, y_test = train_test_split(x, y, test_size=0.2)
# print(X_train.shape)
# print(X_test.shape)

from keras.applications.vgg16 import VGG16


vgg = VGG16()
vgg.summary()


from keras import Sequential


model = Sequential()


for layer in vgg.layers[:-1]:
    model.add(layer)


model.summary()
for layer in model.layers:
    layer.trainable = False


model.summary()


from keras.layers import Dense


model.add(Dense(1, activation='sigmoid'))
model.summary()

model.compile(optimizer='Adam', loss='binary_crossentropy', metrics=['accuracy'])
model.fit(X_train, y_train, epochs=3, steps_per_epoch=3, batch_size=2, validation_data=(X_test, y_test))
haar = cv2.CascadeClassifier(
        r'C:\Users\Joss\AppData\Roaming\Python\Python310\site-packages\cv2\data\haarcascade_frontalface_default.xml')


def detect_fruit1(img):
    coods = haar.detectMultiScale(img)
    return coods


import pyttsx3

engine = pyttsx3.init()


def detect_fruit(img):
    y_pred = model.predict(img.reshape(1, image_size, image_size, 3))
    return y_pred[0][0]


def draw_label(img, text, pos, bg_color):
    text_size = cv2.getTextSize(text, cv2.FONT_HERSHEY_SIMPLEX, 1, cv2.FILLED)
    end_x = pos[0] + text_size[0][0] + 2
    end_y = pos[1] + text_size[0][1] - 2

    cv2.rectangle(img, pos, (end_x, end_y), bg_color, cv2.FILLED)
    cv2.putText(img, text, pos, cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 0), 1, cv2.LINE_AA)


cap = cv2.VideoCapture(0)
while True:
    ret, frame = cap.read()
    # call the detect method
    img = cv2.resize(frame, (image_size, image_size))

    y_pred = detect_fruit(img)

    coods = detect_fruit1(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
    for x, y, w, h in coods:
        cv2.rectangle(frame, (x, y), (x + w, y + h), (255, 0, 0), 3)
    # print("y_predicte is: ", y_pred)
    if y_pred < 0.5:
        prediction = "The fruit is classified as edible"

        draw_label(frame, "edible fruit", (30, 30), (0, 255, 0))
        sound.play()
        text = prediction
        engine.say(text)
        engine.runAndWait()
    else:
        prediction = "The fruit is classified as inedible"
        # sound1.play()
        draw_label(frame, "not edible fruit", (30, 30), (0, 0, 255))
        text = prediction
        engine.say(text)
        engine.runAndWait()
    # print(y_pred)
    cv2.imshow("window", frame)
    k = cv2.waitKey(30) & 0xff

    if k == 27:
        break
    # if cv2.waitKey(1) & 0xff == ord('x'):
        # break
cv2.destroyAllWindows()















