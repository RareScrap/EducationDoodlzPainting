package com.rarescrap.mydoodlz;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

/**
 * Класс MainActivityFragment отображает представление
 * MainActivityView, управляет командами на панели приложения и в меню,
 * а также обработкой событий для функции стирания рисунка встряхиванием устройства.
 */
public class MainActivityFragment extends Fragment {
    private DoodleView doodleView; // Обработка событий касания и рисования
    private float acceleration;
    private float currentAcceleration;
    private float lastAcceleration;
    private boolean dialogOnScreen = false;

    // Используется для обнаружения встряхивания устройства
    private static final int ACCELERATION_THRESHOLD = 100000;

    // Используется для идентификации запросов на использование
    // внешнего хранилища; необходимо для работы функции сохранения
    private static final int SAVE_IMAGE_PERMISSION_REQUEST_CODE = 1;

    // Вызывается при создании представления фрагмента
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view =
                inflater.inflate(R.layout.fragment_main, container, false);

        setHasOptionsMenu(true); // у фрагмента имеются команды меню

        // Получение ссылки на DoodleView
        doodleView = (DoodleView) view.findViewById(R.id.doodleView);

        // Инициализация параметров ускорения
        acceleration = 0.00f;
        currentAcceleration = SensorManager.GRAVITY_EARTH;
        lastAcceleration = SensorManager.GRAVITY_EARTH;
        return view;
    }

    // Начало прослушивания событий датчика
    @Override
    public void onResume() {
        super.onResume();
        enableAccelerometerListening(); // Прослушивание события встряхивания
    }

    // Включение прослушивания событий акселерометра
    private void enableAccelerometerListening() {
        // Получение объекта SensorManager
        SensorManager sensorManager =
                (SensorManager) getActivity().getSystemService(
                        Context.SENSOR_SERVICE);

        // Регистрация для прослушивания событий акселерометра
        sensorManager.registerListener(sensorEventListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    // Прекращение прослушивания событий акселерометра
    @Override
    public void onPause() {
        super.onPause();
        disableAccelerometerListening(); // Регистрация для прослушивания событий акселерометра
    }

    // Отказ от прослушивания событий акселерометра
    private void disableAccelerometerListening() {
        // Получение объекта SensorManager
        SensorManager sensorManager =
                (SensorManager) getActivity().getSystemService(
                        Context.SENSOR_SERVICE);

        // Прекращение прослушивания событий акселерометра
        sensorManager.unregisterListener(sensorEventListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
    }

    // Обработчик для событий акселерометра
    private final SensorEventListener sensorEventListener =
            new SensorEventListener() {
                // Проверка встряхивания по показаниям акселерометра
                @Override
                public void onSensorChanged(SensorEvent event) {
                    // На экране не должно быть других диалоговых окон
                    if (!dialogOnScreen) {
                        // Получить значения x, y и z для SensorEvent
                        float x = event.values[0];
                        float y = event.values[1];
                        float z = event.values[2];

                        // Сохранить предыдущие данные ускорения
                        lastAcceleration = currentAcceleration;

                        // Вычислить текущее ускорение
                        currentAcceleration = x * x + y * y + z * z;

                        // Вычислить изменение ускорения
                        acceleration = currentAcceleration *
                                (currentAcceleration - lastAcceleration);

                        // Если изменение превышает заданный порог
                        if (acceleration > ACCELERATION_THRESHOLD)
                            confirmErase();
                    }
                }

                // Обязательный метод интерфейса SensorEventListener
                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            };

    // Подтверждение стирания рисунка
    private void confirmErase() {
        EraseImageDialogFragment fragment = new EraseImageDialogFragment();
        fragment.show(getFragmentManager(), "erase dialog"); //TODO: хз что делает второй арг. Какой-то тег, но почему он не в ресурсах?
    }

    // Отображение команд меню фрагмента
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.doodle_fragment_menu, menu); //TODO: Определять версию смарта и если он старый - грузить меню без печати
    }

    // Обработка выбора команд меню
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Выбор в зависимости от идентификатора MenuItem
        switch (item.getItemId()) {
            case R.id.color:
                ColorDialogFragment colorDialog = new ColorDialogFragment();
                colorDialog.show(getFragmentManager(), "color dialog");
                return true; // Событие меню обработано
            case R.id.line_width:
                LineWidthDialogFragment widthDialog =
                        new LineWidthDialogFragment();
                widthDialog.show(getFragmentManager(), "line width dialog");
                return true; // Событие меню обработано
            case R.id.delete_drawing:
                confirmErase(); // Получить подтверждение перед стиранием
                return true; // Событие меню обработано
            case R.id.save:
                saveImage(); // Проверить разрешение и сохранить рисунок
                return true; // Событие меню обработано
            case R.id.print:
                doodleView.printImage(); // // Напечатать текущий рисунок
                return true; // Событие меню обработано
        }

        return super.onOptionsItemSelected(item); //TODO: Разобраться зачем вообще тут нужен супер
    }

    // При необходимости метод запрашивает разрешение
    // или сохраняет изображение, если разрешение уже имеется
    private void saveImage() {
        // Проверить, есть ли у приложения разрешение,
        // необходимое для сохранения
        if (getContext().checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {

            // Объяснить, почему понадобилось разрешение
            if (shouldShowRequestPermissionRationale(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                AlertDialog.Builder builder =
                        new AlertDialog.Builder(getActivity());

                // Назначить сообщение AlertDialog
                builder.setMessage(R.string.permission_explanation);

                // Добавить кнопку OK в диалоговое окно
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Запросить разрешение
                                requestPermissions(new String[]{ // Нужен еще параметр целевой активити, но скорее всего эта форма метода для текущего активити.
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        SAVE_IMAGE_PERMISSION_REQUEST_CODE); //TODO: В доках написано что должен быть еще один первый арг - целеое активити. Разобраться почему работает без него
                            }
                        }
                );

                // Отображение диалогового окна
                builder.create().show();
            }
            else {
                // Запросить разрешение
                requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        SAVE_IMAGE_PERMISSION_REQUEST_CODE);
            }
        }
        else { // Если разрешение уже имеет разрешение для записи
            doodleView.saveImage(); // Сохранить изображение
        }
    }

    // Вызывается системой, когда пользователь предоставляет
    // или отклоняет разрешение для сохранения изображения
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) { //TODO: Обработать ситуацию, когда юзер не дал разрешения, но нажал кнопку "не показывать снова" окно запроса разрешения
        // switch выбирает действие в зависимости от того,
        // какое разрешение было запрошено
        switch (requestCode) {
            case SAVE_IMAGE_PERMISSION_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    doodleView.saveImage(); // Сохранить изображение
                // TODO: Реализовать тост-уведомление, когда доступ не предостален и нажат флаг "больше не показывать"
                return;
        }
    }
    
    // Метод возвращает объект DoodleView
    public DoodleView getDoodleView() {
        return doodleView;
    }

    // Проверяет, отображается ли диалоговое окно
    public void setDialogOnScreen(boolean visible) {
        dialogOnScreen = visible;
   }
}
