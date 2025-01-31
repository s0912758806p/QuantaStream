package com.regy.quantalink.quickstart.connector.nebula.process;

import com.regy.quantalink.common.type.TypeInformation;
import com.regy.quantalink.common.utils.HashUtils;
import com.regy.quantalink.common.utils.TimeUtils;
import com.regy.quantalink.flink.core.connector.SinkConnector;
import com.regy.quantalink.flink.core.connector.nebula.operator.GraphKeyedBroadcastProcessFunc;
import com.regy.quantalink.flink.core.connector.nebula.sink.NebulaSinkConnector;
import com.regy.quantalink.quickstart.connector.nebula.entity.DcsEvent;
import com.regy.quantalink.quickstart.connector.nebula.entity.NebulaTag;
import com.regy.quantalink.quickstart.connector.nebula.entity.SensorMap;

import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author regy
 */
public class GenerateWorkOrderGraph extends GraphKeyedBroadcastProcessFunc<String, DcsEvent, SensorMap> {
    private final MapStateDescriptor<String, Map<String, List<String>>> stateDesc;
    private transient MapState<String, Map<String, WorkOrderRecord>> workOrderBuffer;
    private static final String WORK_ORDER_BUFFER = "WORK_ORDER_BUFFER";
    private volatile boolean isReady = false;

    public GenerateWorkOrderGraph(Map<TypeInformation<?>, SinkConnector<?,?>> connectorMap, MapStateDescriptor<String, Map<String, List<String>>> stateDesc) {
        super(connectorMap);
        this.stateDesc = stateDesc;
    }

    @Override
    public void open(Configuration parameters) {
        workOrderBuffer = this.getRuntimeContext().getMapState(new MapStateDescriptor<>(WORK_ORDER_BUFFER, Types.STRING, Types.MAP(Types.STRING, Types.GENERIC(WorkOrderRecord.class))));
    }

    @Override
    protected void processGraph(
            DcsEvent record,
            KeyedBroadcastProcessFunction<String, DcsEvent, SensorMap, Void>.ReadOnlyContext readOnlyCtx,
            Collector<Void> collector,
            Map<TypeInformation<?>, SinkConnector<?,?>> connectorMap) throws Exception {

        NebulaSinkConnector orderSinkConnector =
                (NebulaSinkConnector) connectorMap.get(TypeInformation.get(NebulaTag.WorkOrder.class));

        readOnlyCtx.output(
                orderSinkConnector.getOutputTag(),
                createWorkOrderRow(record.moNumber, orderSinkConnector.getRowArity()));

        if (isReady) {

            ReadOnlyBroadcastState<String, Map<String, List<String>>> state = readOnlyCtx.getBroadcastState(stateDesc);
            NebulaSinkConnector producedOnSinkConnector =
                    (NebulaSinkConnector) connectorMap.get(TypeInformation.get(NebulaTag.ProducedOn.class));

            for (Map.Entry<String, Map<String, List<String>>> entry : state.immutableEntries()) {
                List<String> stationIds = entry.getValue().get(record.dcsId);
                if (stationIds != null) {
                    for (String stationId : stationIds) {
                        readOnlyCtx.output(
                                producedOnSinkConnector.getOutputTag(),
                                createProducedOnRow(record.moNumber, stationId, addToBuffer(record, stationId), producedOnSinkConnector.getRowArity()));
                    }
                }
            }
        }
    }

    @Override
    public void processBroadcastElement(SensorMap sensorMap, KeyedBroadcastProcessFunction<String, DcsEvent, SensorMap, Void>.Context ctx, Collector<Void> collector) throws Exception {
        BroadcastState<String, Map<String, List<String>>> state = ctx.getBroadcastState(stateDesc);
        state.put(sensorMap.oid, extractSequenceInfo(sensorMap));
        isReady = true;
    }

    private WorkOrderRecord addToBuffer(DcsEvent record, String stationId) throws Exception {
        Map<String, WorkOrderRecord> elemsInMap = workOrderBuffer.get(record.moNumber);
        if (elemsInMap == null) {
            elemsInMap = new HashMap<>(256);
        }

        WorkOrderRecord workOrderRecord = elemsInMap.get(stationId);
        if (workOrderRecord == null) {
            workOrderRecord =
                    new WorkOrderRecord(1L, TimeUtils.toMillis(record.startedAt), TimeUtils.toMillis(record.endedAt));
        } else {
            ++workOrderRecord.producedCount;
            workOrderRecord.endedAt = TimeUtils.toMillis(record.endedAt);
        }

        elemsInMap.put(stationId, workOrderRecord);
        workOrderBuffer.put(record.moNumber, elemsInMap);

        return workOrderRecord;
    }

    private Map<String, List<String>> extractSequenceInfo(SensorMap sensorMap) {
        Map<String, String> stationMap = extractStationInfo(sensorMap);
        Map<String, List<String>> sequenceMap = new HashMap<>(256);

        for (SensorMap.ProductionLine line : sensorMap.productionLines) {
            for (SensorMap.SequenceInfo sequenceInfo : line.sequence) {
                String baseStationId = stationMap.get(sequenceInfo.baseOid);
                String workStationId = stationMap.get(sequenceInfo.workstationOid);
                if (sequenceMap.containsKey(baseStationId)) {
                    sequenceMap.get(baseStationId).add(workStationId);
                } else {
                    sequenceMap.put(baseStationId, new ArrayList<>(List.of(workStationId)));
                }
            }
        }

        return sequenceMap;
    }

    private Map<String, String> extractStationInfo(SensorMap sensorMap) {
        Map<String, String> stationMap = new HashMap<>(256);

        for (SensorMap.ProductionLine line : sensorMap.productionLines) {
            for (SensorMap.Workstation station : line.workstations) {
                stationMap.put(station.oid, station.stationId);
            }
        }

        return stationMap;
    }

    private Row createWorkOrderRow(String workOrderId, Integer rowArity) throws NoSuchAlgorithmException {
        Row row = new Row(rowArity);

        row.setField(0, HashUtils.hash(workOrderId));
        row.setField(1, workOrderId);

        return row;
    }

    private Row createProducedOnRow(
            String workOrderId, String stationId, WorkOrderRecord workOrderRecord, Integer rowArity) throws NoSuchAlgorithmException {

        Row row = new Row(rowArity);

        row.setField(0, HashUtils.hash(workOrderId));
        row.setField(1, HashUtils.hash(stationId));
        row.setField(2, TimeUtils.toDateTime(workOrderRecord.startedAt).toString());
        row.setField(3, TimeUtils.toDateTime(workOrderRecord.endedAt).toString());
        row.setField(4, workOrderRecord.producedCount.toString());

        return row;
    }

    private static class WorkOrderRecord {
        private Long producedCount;
        private final Long startedAt;
        private Long endedAt;

        public WorkOrderRecord(Long count, Long startedAt, Long endedAt) {
            this.producedCount = count;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
        }
    }
}
